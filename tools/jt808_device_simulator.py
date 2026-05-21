#!/usr/bin/env python3
"""JT808 终端模拟：注册(0x0100)、心跳(0x0002)。"""

import argparse
import socket
import struct
import time

FLAG = 0x7E
ESCAPE = 0x7D


def xor_cs(data: bytes) -> int:
    x = 0
    for b in data:
        x ^= b
    return x


def escape(data: bytes) -> bytes:
    out = bytearray()
    for b in data:
        if b == FLAG:
            out.extend([ESCAPE, 0x02])
        elif b == ESCAPE:
            out.extend([ESCAPE, 0x01])
        else:
            out.append(b)
    return bytes(out)


def bcd_phone(phone: str) -> bytes:
    digits = "".join(c for c in phone if c.isdigit())
    if len(digits) % 2:
        digits = "0" + digits
    digits = digits.zfill(12)[-12:]
    raw = bytearray(6)
    for i in range(6):
        raw[i] = (int(digits[i * 2]) << 4) | int(digits[i * 2 + 1])
    return bytes(raw)


def build_frame(msg_id: int, phone: str, serial: int, body: bytes = b"") -> bytes:
    props = len(body) & 0x03FF
    raw = bytearray()
    raw += struct.pack(">H", msg_id)
    raw += struct.pack(">H", props)
    raw += bcd_phone(phone)
    raw += struct.pack(">H", serial)
    raw += body
    raw.append(xor_cs(raw))
    return bytes([FLAG]) + escape(bytes(raw)) + bytes([FLAG])


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=9001)
    p.add_argument("--phone", default="13800138000")
    args = p.parse_args()

    sock = socket.create_connection((args.host, args.port), timeout=10)
    print(f"connected {args.host}:{args.port}")

    sock.sendall(build_frame(0x0100, args.phone, 1, b"\x00\x00"))
    print(">> register 0x0100")
    time.sleep(1)

    for i in range(2, 5):
        time.sleep(3)
        sock.sendall(build_frame(0x0002, args.phone, i))
        print(">> heartbeat", i)

    time.sleep(10)
    sock.close()


if __name__ == "__main__":
    main()
