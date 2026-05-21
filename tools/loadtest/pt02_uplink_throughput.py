#!/usr/bin/env python3
"""PT-02：多连接持续上行，统计发送条数与耗时。"""

import argparse
import json
import socket
import struct
import threading
import time

MAGIC = b"OMNI"


def checksum(magic: bytes, body: bytes) -> int:
    x = 0
    for b in magic + body:
        x ^= b
    return x & 0xFF


def encode_frame(obj: dict) -> bytes:
    body = json.dumps(obj, separators=(",", ":")).encode("utf-8")
    return MAGIC + struct.pack(">H", len(body)) + body + bytes([checksum(MAGIC, body)])


def worker(host: str, port: int, device_id: str, duration: float, stats: dict):
    sent = 0
    try:
        s = socket.create_connection((host, port), timeout=5)
        s.sendall(encode_frame({"type": "auth", "deviceId": device_id}))
        s.recv(4096)
        end = time.time() + duration
        while time.time() < end:
            s.sendall(encode_frame({"type": "telemetry", "payload": {"t": time.time()}}))
            sent += 1
        s.close()
    except OSError:
        pass
    stats[device_id] = sent


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=9000)
    p.add_argument("--devices", type=int, default=50)
    p.add_argument("--duration-sec", type=int, default=60)
    args = p.parse_args()

    stats = {}
    threads = []
    start = time.time()
    for i in range(args.devices):
        t = threading.Thread(
            target=worker,
            args=(args.host, args.port, f"pt02-{i}", float(args.duration_sec), stats),
            daemon=True,
        )
        threads.append(t)
        t.start()
    for t in threads:
        t.join()
    elapsed = time.time() - start
    total = sum(stats.values())
    print(f"devices={args.devices} duration={args.duration_sec}s total_msgs={total}")
    print(f"aggregate_rate={total / elapsed:.1f} msg/s")
    print("Prometheus: rate(omni_messages_uplink_total[1m])")


if __name__ == "__main__":
    main()
