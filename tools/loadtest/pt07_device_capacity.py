#!/usr/bin/env python3
"""
PT-07：设备长连接容量压测（simple-frame / 端口 9000）。

在 PT-01 基础上增加：渐进建连、存活统计、可选拉取网关 Prometheus 指标。

示例：
  python tools/loadtest/pt07_device_capacity.py --connections 5000 --ramp-sec 120 --duration-sec 300
  python tools/loadtest/pt07_device_capacity.py --connections 1000 --metrics-url http://127.0.0.1:8080/actuator/prometheus

注意：默认 omni.security.connection-rate-per-ip=50，单机压测时请加 --connect-rate 40 或调大网关限流。
"""

from __future__ import annotations

import argparse
import re
import socket
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field

from omni_simple_frame import encode_frame

PROM_ACTIVE = re.compile(
    r"^omni_connections_active(?:\{[^}]*\})?\s+([0-9.eE+-]+)\s*$",
    re.MULTILINE,
)


@dataclass
class Stats:
    attempted: int = 0
    connected: int = 0
    failed: int = 0
    heartbeat_errors: int = 0
    lock: threading.Lock = field(default_factory=threading.Lock)

    def inc_attempted(self) -> None:
        with self.lock:
            self.attempted += 1

    def inc_connected(self) -> None:
        with self.lock:
            self.connected += 1

    def inc_failed(self) -> None:
        with self.lock:
            self.failed += 1

    def inc_heartbeat_error(self) -> None:
        with self.lock:
            self.heartbeat_errors += 1


def connect_one(host: str, port: int, device_id: str, timeout: float) -> socket.socket | None:
    try:
        s = socket.create_connection((host, port), timeout=timeout)
        s.settimeout(timeout)
        s.sendall(encode_frame({"type": "auth", "deviceId": device_id}))
        return s
    except OSError:
        return None


def fetch_gateway_active(url: str, timeout: float) -> float | None:
    try:
        req = urllib.request.Request(url, headers={"Accept": "text/plain"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="replace")
        m = PROM_ACTIVE.search(body)
        return float(m.group(1)) if m else None
    except (urllib.error.URLError, OSError, ValueError):
        return None


def ramp_connect(
    host: str,
    port: int,
    prefix: str,
    total: int,
    ramp_sec: float,
    connect_rate: float,
    timeout: float,
    stats: Stats,
    sockets: list[socket.socket],
    sock_lock: threading.Lock,
) -> None:
    if total <= 0:
        return
    start = time.time()
    rate_sleep = (1.0 / connect_rate) if connect_rate > 0 else 0.0
    for i in range(total):
        stats.inc_attempted()
        device_id = f"{prefix}-{i}"
        s = connect_one(host, port, device_id, timeout)
        if s:
            with sock_lock:
                sockets.append(s)
            stats.inc_connected()
        else:
            stats.inc_failed()
        if ramp_sec > 0:
            target = start + (i + 1) * ramp_sec / total
            delay = target - time.time()
            if delay > 0:
                time.sleep(delay)
        elif rate_sleep > 0 and i + 1 < total:
            time.sleep(rate_sleep)


def heartbeat_loop(
    sockets: list[socket.socket],
    sock_lock: threading.Lock,
    interval: float,
    stop: threading.Event,
    stats: Stats,
) -> None:
    while not stop.wait(interval):
        with sock_lock:
            snapshot = list(sockets)
        dead: list[socket.socket] = []
        for s in snapshot:
            try:
                s.sendall(encode_frame({"type": "telemetry", "payload": {"ping": True}}))
            except OSError:
                stats.inc_heartbeat_error()
                dead.append(s)
        if dead:
            with sock_lock:
                for s in dead:
                    if s in sockets:
                        sockets.remove(s)
                    try:
                        s.close()
                    except OSError:
                        pass


def reporter(
    stats: Stats,
    sockets: list[socket.socket],
    sock_lock: threading.Lock,
    metrics_url: str | None,
    metrics_timeout: float,
    interval: float,
    stop: threading.Event,
) -> None:
    while not stop.wait(interval):
        with sock_lock:
            client_active = len(sockets)
        server_active = fetch_gateway_active(metrics_url, metrics_timeout) if metrics_url else None
        with stats.lock:
            att, ok, fail, hb_err = stats.attempted, stats.connected, stats.failed, stats.heartbeat_errors
        line = (
            f"[{time.strftime('%H:%M:%S')}] client_active={client_active} "
            f"connected={ok}/{att} failed={fail} hb_errors={hb_err}"
        )
        if server_active is not None:
            line += f" gateway_omni_connections_active={server_active:.0f}"
        print(line, flush=True)


def main() -> None:
    p = argparse.ArgumentParser(description="PT-07 device long-connection capacity test")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=9000)
    p.add_argument("--connections", type=int, default=1000, help="目标鉴权长连接数")
    p.add_argument("--duration-sec", type=int, default=300, help="保持连接时长")
    p.add_argument("--ramp-sec", type=float, default=60.0, help="建连窗口（0=尽快建完）")
    p.add_argument(
        "--connect-rate",
        type=float,
        default=40.0,
        help="建连速率（连接/秒），应低于 connection-rate-per-ip（默认 50）",
    )
    p.add_argument("--connect-timeout", type=float, default=5.0)
    p.add_argument("--heartbeat-interval", type=float, default=30.0)
    p.add_argument("--report-interval", type=float, default=10.0)
    p.add_argument("--device-prefix", default="cap")
    p.add_argument(
        "--metrics-url",
        default="",
        help="Prometheus 端点，如 http://127.0.0.1:8080/actuator/prometheus",
    )
    p.add_argument("--metrics-timeout", type=float, default=3.0)
    args = p.parse_args()

    metrics_url = args.metrics_url.strip() or None
    stats = Stats()
    sockets: list[socket.socket] = []
    sock_lock = threading.Lock()
    stop = threading.Event()

    print(
        f"PT-07 start host={args.host}:{args.port} target={args.connections} "
        f"ramp={args.ramp_sec}s rate={args.connect_rate}/s hold={args.duration_sec}s"
    )
    t0 = time.time()
    ramp_connect(
        args.host,
        args.port,
        args.device_prefix,
        args.connections,
        args.ramp_sec,
        args.connect_rate,
        args.connect_timeout,
        stats,
        sockets,
        sock_lock,
    )
    ramp_elapsed = time.time() - t0
    with sock_lock:
        active_after_ramp = len(sockets)
    print(
        f"ramp done in {ramp_elapsed:.1f}s active={active_after_ramp}/{args.connections} "
        f"(failed={stats.failed})"
    )

    threads = [
        threading.Thread(
            target=heartbeat_loop,
            args=(sockets, sock_lock, args.heartbeat_interval, stop, stats),
            daemon=True,
        ),
        threading.Thread(
            target=reporter,
            args=(sockets, sock_lock, metrics_url, args.metrics_timeout, args.report_interval, stop, stats),
            daemon=True,
        ),
    ]
    for t in threads:
        t.start()

    hold_start = time.time()
    time.sleep(args.duration_sec)
    stop.set()
    hold_elapsed = time.time() - hold_start

    with sock_lock:
        final_active = len(sockets)
        for s in sockets:
            try:
                s.close()
            except OSError:
                pass

    server_final = fetch_gateway_active(metrics_url, args.metrics_timeout) if metrics_url else None
    success_rate = (stats.connected / stats.attempted * 100) if stats.attempted else 0.0

    print("--- summary ---")
    print(f"attempted={stats.attempted} connected={stats.connected} failed={stats.failed}")
    print(f"success_rate={success_rate:.1f}% final_client_active={final_active}")
    print(f"hold_sec={hold_elapsed:.0f} heartbeat_errors={stats.heartbeat_errors}")
    if server_final is not None:
        print(f"gateway_omni_connections_active={server_final:.0f}")
    print("fill BASELINE-REPORT: 单节点并发连接 =", final_active)


if __name__ == "__main__":
    main()
