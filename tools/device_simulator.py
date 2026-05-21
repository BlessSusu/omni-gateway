#!/usr/bin/env python3
"""Simple-frame 设备模拟器：鉴权、上行、下行 ACK。"""

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


def read_frame(sock: socket.socket) -> dict | None:
    header = _recv_exact(sock, 6)
    if not header:
        return None
    if header[:4] != MAGIC:
        raise ValueError("bad magic")
    length = struct.unpack(">H", header[4:6])[0]
    rest = _recv_exact(sock, length + 1)
    if not rest:
        return None
    body, cs = rest[:length], rest[length]
    if checksum(MAGIC, body) != cs:
        raise ValueError("checksum error")
    return json.loads(body.decode("utf-8"))


def _recv_exact(sock: socket.socket, n: int) -> bytes | None:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=9000)
    p.add_argument("--device-id", default="device-001")
    args = p.parse_args()

    sock = socket.create_connection((args.host, args.port), timeout=10)
    # 鉴权后取消全局超时：上行 telemetry 无 TCP 回包，读线程只等服务端下行
    sock.settimeout(None)
    print(f"connected {args.host}:{args.port}")

    sock.sendall(encode_frame({"type": "auth", "deviceId": args.device_id}))
    resp = read_frame(sock)
    print("auth response:", resp)

    stop_reader = threading.Event()

    def reader():
        while not stop_reader.is_set():
            try:
                msg = read_frame(sock)
                if msg is None:
                    break
                print("<< downlink", msg)
                if msg.get("messageId"):
                    sock.sendall(encode_frame({
                        "type": "ack",
                        "messageId": msg["messageId"],
                    }))
                    print(">> ack", msg["messageId"])
            except OSError as e:
                if not stop_reader.is_set():
                    print("reader stopped:", e)
                break

    threading.Thread(target=reader, daemon=True).start()

    for i in range(3):
        time.sleep(2)
        sock.sendall(encode_frame({
            "type": "telemetry",
            "payload": {"seq": i, "temp": 20 + i},
        }))
        print(">> telemetry", i, "(no TCP reply; check Kafka topic omni.device.uplink)")

    print("waiting for downlink (Ctrl+C to exit)...")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        stop_reader.set()
        sock.close()


if __name__ == "__main__":
    main()
