# 详细设计 03：传输安全（Transport Security）

| 属性 | 值 |
|------|-----|
| 状态 | **已实现**（TLS/限流/黑白名单；国密未做） |
| 依赖模块 | omni-network, omni-bootstrap |
| 关联设计 | [04-observability](./04-observability.md) |

---

## 1. 背景与目标

### 1.1 问题

- 明文 TCP 易被窃听、篡改；物联网场景常要求传输加密与设备身份校验
- 嗅探阶段若允许任意首包，攻击面包括：资源耗尽、协议探测
- 密钥、证书需支持轮换而不长时间停服

### 1.2 目标

| 目标 | 说明 |
|------|------|
| 传输机密性与完整性 | TLS 1.2+（推荐 1.3） |
| 设备身份 | 支持服务端认证 + 可选 mTLS 客户端证书 |
| 接入层防护 | 连接速率限制、黑白名单、嗅探失败审计 |
| 密钥轮换 | 证书双证加载、配置热更新 |

### 1.3 非目标

- 应用层端到端加密（业务 Payload 自行加密）——由业务负责
- 国密 SM2/SM4（若有合规要求单独立项）

---

## 2. TLS 架构

### 2.1 Pipeline 位置

```
明文模式:
  TCP -> SniffHandler -> ...

TLS 模式 (推荐):
  TCP -> SslHandler -> SniffHandler -> ...
```

TLS 必须在嗅探之前，帧头嗅探针对**明文应用层**字节流。

### 2.2 部署模式

| 模式 | 说明 | 适用 |
|------|------|------|
| **网关终结 TLS** | LB 透传 TCP，网关 `SslHandler` | **推荐**，证书集中在网关 |
| LB 终结 TLS | LB 解密后以明文转发内网 | 内网可信时简化网关 |
| mTLS | 要求客户端证书 | 高安全设备 |

**待确认项 A**：生产默认「网关终结」还是「LB 终结」。

### 2.3 Netty SslContext 构建

```java
// 伪代码 - omni-network
SslContextFactory.create(SslProperties props):
  if props.mtlsEnabled
    return SslContextBuilder.forServer(serverCert, key)
      .trustManager(clientCa)
      .clientAuth(ClientAuth.REQUIRE)
  else
    return SslContextBuilder.forServer(serverCert, key)
      .clientAuth(ClientAuth.NONE)
```

证书来源优先级：

1. 本地文件路径（`classpath` 或绝对路径）
2. Nacos 配置（Base64 PEM，**仅限内网**；大证书建议文件挂载）

---

## 3. 证书与密钥轮换

### 3.1 双证书策略

同时加载：

- `current`：对外生效
- `next`：已签发未切换

`SslContext` 重建时优先使用 current；握手失败率上升时可回滚。

### 3.2 轮换流程

```
1. 运维上传 next 证书到配置中心 / K8s Secret
2. 触发配置刷新 -> SslContextRef.reload()
3. 仅影响新连接；老连接保持原 Session
4. 观察 handshake_error 指标 24h
5. 将 next 提升为 current，移除旧证
```

### 3.3 配置模型

```yaml
omni:
  tls:
    enabled: true
    port-bindings:   # 可按端口差异化
      - port: 8443
        enabled: true
        cert-chain: /etc/omni/certs/server.pem
        private-key: /etc/omni/certs/server.key
        client-auth: OPTIONAL  # NONE | OPTIONAL | REQUIRE
        trust-store: /etc/omni/certs/ca.pem
    protocols: [TLSv1.2, TLSv1.3]
    cipher-suites: []  # 空则 JVM 默认强套件
```

---

## 4. 设备身份与鉴权分层

```
┌─────────────────────────────────────────┐
│ L1 传输层: TLS + 可选 mTLS 客户端证书    │
├─────────────────────────────────────────┤
│ L2 连接层: IP 黑白名单、速率限制         │
├─────────────────────────────────────────┤
│ L3 协议层: Plugin.authenticate()        │
│         如 JT808 终端注册、Token         │
└─────────────────────────────────────────┘
```

### 4.1 mTLS 身份映射

客户端证书 `subject DN` 或 `serialNumber` 映射为 `deviceId` 候选：

```yaml
omni:
  tls:
    mtls-device-id-extractor: SUBJECT_CN  # SUBJECT_CN | SAN_URI
```

可与协议鉴权交叉验证：**证书 CN 与协议上报 ID 不一致则拒绝**。

**待确认项 B**：mTLS 映射的 deviceId 是否作为鉴权唯一依据。

### 4.2 协议层鉴权（已有机制强化）

`ProtocolPlugin.authenticate(Session, firstMessage)` 返回：

```java
enum AuthResult { OK, FAIL, PENDING }
```

失败：关闭连接 + 审计日志 + metric `auth_failure_total`。

---

## 5. 接入防护

### 5.1 连接速率限制

| 粒度 | 实现 | 默认 |
|------|------|------|
| 单 IP 新建连接 | Guava RateLimiter 或令牌桶 | 50/s |
| 全局新建连接 | 全局限流 | 2000/s |

超限：`channel.close()` + `connection_rejected_total{reason=rate_limit}`。

### 5.2 黑白名单

```yaml
omni:
  security:
    ip-allow-list: []      # 非空则仅允许列表内
    ip-deny-list: []
```

在 `ChannelInitializer` 最早阶段检查 `remoteAddress`。

### 5.3 嗅探安全（与 §6 流程一致）

- `Max_Sniff_Bytes` / `Sniff_Timeout` 强制生效
- 嗅探失败记录 IP + 前 16 字节 hex（**不落盘完整 payload**，隐私合规）

---

## 6. 模块职责

| 模块 | 组件 |
|------|------|
| omni-network | `TlsChannelInitializer`, `SslContextRef`, `IpFilterHandler`, `ConnectionRateLimiter` |
| omni-bootstrap | `SslProperties`, 监听 Nacos 证书变更 |
| omni-core | `AuthResult`, 审计事件模型 |

---

## 7. 失败处理

| 事件 | 处理 |
|------|------|
| TLS 握手失败 | 关闭；metric + 采样日志 |
| 证书过期 | 启动时 WARN；过期前 30 天告警 |
| 配置加载失败 | 保持旧 SslContext，拒绝切换 |
| mTLS 无客户端证 | 按 `client-auth=REQUIRE` 拒绝 |

---

## 8. 验收标准

- [ ] TLS 1.2 握手成功，嗅探与业务正常
- [ ] mTLS 模式：无客户端证拒绝；有效证 + 协议鉴权通过
- [ ] 证书热更新后新连接使用新证，老连接不受影响
- [ ] 单 IP 超限触发限流与 metric
- [ ] 嗅探失败有审计日志且无连接泄漏

---

## 9. 待确认清单

| 编号 | 问题 | 建议默认 |
|------|------|----------|
| A | TLS 终结位置 | 网关终结 |
| B | mTLS 与协议 ID 关系 | 交叉校验，不一致拒绝 |
| C | 是否首期启用 mTLS | 否，预留配置，先服务端 TLS |
| D | 国密需求 | 无则不做 |

**请确认后开发。**
