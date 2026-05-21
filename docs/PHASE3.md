# Phase 3 — 业务协议与下行增强

| 属性 | 值 |
|------|-----|
| 版本 | v1.0 |
| 状态 | **开发中** |
| 前置 | [Phase 2](./PHASE2.md) 生产化能力已交付 |

---

## 阶段目标

在第二期多节点生产基座上，优先补齐 **行业协议（GB28181）**，再交付离线下发、广播与控制面；**网关 gRPC 集群转发纳入第四期**。

建议周期：**6～8 周**（GB28181 约 2～3 周）。

---

## 里程碑（推荐顺序）

```
M18 GB28181 协议插件（SIP 解析、REGISTER/MESSAGE、MANSCDP+xml）  ← 已实现解析
    └─► M19 GB28181 下行（INVITE/MESSAGE 推流 URL、Catalog 查询）
            └─► M20 离线下行补发 + 设备影子
                    └─► M21 下行广播 + 批量 filter
                            └─► M22 控制面 API + Nacos 生产化
```

**第四期**（见 [PHASE4.md](./PHASE4.md)）：gRPC 下行转发、一致性哈希、国密（按需）。

---

## M18：GB28181 协议解析（优先）

| 能力 | 说明 |
|------|------|
| 嗅探 | SIP 起始行：`REGISTER` / `MESSAGE` / `NOTIFY` / `INVITE` / `SIP/2.0` |
| 帧界 | TCP：`\r\n\r\n` + `Content-Length` 定界 |
| 鉴权 | `REGISTER`：从 `From`/`Contact` 解析 `DeviceID`（20 位国标编码） |
| 上行 | `MESSAGE`/`NOTIFY` + `Application/MANSCDP+xml` → ThingModel（`CmdType`、`DeviceID`、原始 XML） |
| 响应 | `REGISTER` → `SIP/2.0 200 OK` |
| 端口 | 建议独立监听 **5060**（TCP），与 JT808/simple-frame 分离 |

### 配置示例

```yaml
omni:
  gateway:
    listeners:
      - port: 5060
        plugins: [gb28181]
        plugin-priority: [gb28181]
        sniff:
          max-bytes: 4096
          timeout-ms: 8000
          min-probe-length: 7
```

### 验收

- [x] SIP 解析：`REGISTER` / `MESSAGE` + `Content-Length` 定界  
- [x] `REGISTER` → `200 OK` + `deviceId` 绑定  
- [x] `Keepalive` 等 XML → ThingModel（`cmdType`、`xml`）  
- [x] 单元测试 `Gb28181CodecTest`  
- [ ] 联调：Redis 索引 + 5060 端口 E2E（需设备或模拟器）  

---

## M19～M22（后续）

| ID | 内容 | 说明 |
|----|------|------|
| M19 | GB28181 下行 | `DeviceControl` 推流、`Broadcast` 等 COMMAND 编码 |
| M20 | 离线下行 | pending 队列 + 上线补发 |
| M21 | 广播 | `omni.command.downlink.broadcast` |
| M22 | 控制面 | 路由查询 API、Nacos L0～L4 |

---

## 第三期明确不做

| 项 | 挪到 |
|----|------|
| 网关间 gRPC 下行转发 | **第四期** |
| 一致性哈希环 | 第四期 |
| 国密 SM2/SM4 | 第四期或合规单独立项 |
| WebSocket 接入 | 不纳入 |

---

## 相关文档

- [PHASES.md](./PHASES.md) — 分期总览  
- [PHASE4.md](./PHASE4.md) — 规模化 / gRPC  
- [design/07-gb28181.md](./design/07-gb28181.md) — GB28181 插件设计  
