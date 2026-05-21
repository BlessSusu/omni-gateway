#!/usr/bin/env python3
"""PT-01：维持大量长连接（仅 auth，周期性心跳 telemetry）。"""

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


def connect_one(host: str, port: int, device_id: str) -> socket.socket | None:
    try:
        s = socket.create_connection((host, port), timeout=5)
        s.sendall(encode_frame({"type": "auth", "deviceId": device_id}))
        return s
    except OSError as e:
        print("connect fail", device_id, e)
        return None


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=9000)
    p.add_argument("--connections", type=int, default=100)
    p.add_argument("--duration-sec", type=int, default=60)
    args = p.parse_args()

    sockets: list[socket.socket] = []
    for i in range(args.connections):
        s = connect_one(args.host, args.port, f"load-{i}")
        if s:
            sockets.append(s)

    print(f"active connections: {len(sockets)}/{args.connections}")

    stop = threading.Event()

    def heartbeat():
        while not stop.is_set():
            for idx, s in enumerate(list(sockets)):
                try:
                    s.sendall(encode_frame({
                        "type": "telemetry",
                        "payload": {"ping": True, "id": idx},
                    }))
                except OSError:
                    pass
            time.sleep(30)

    threading.Thread(target=heartbeat, daemon=True).start()
    time.sleep(args.duration_sec)
    stop.set()
    for s in sockets:
        s.close()
    print("done")


if __name__ == "__main__":
    main()
