#!/usr/bin/env python3
"""PT-05: TLS handshake smoke test against gateway TLS port."""
import argparse
import socket
import ssl


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=9443)
    p.add_argument("--insecure", action="store_true", help="skip cert verification")
    args = p.parse_args()

    ctx = ssl.create_default_context()
    if args.insecure:
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE

    with socket.create_connection((args.host, args.port), timeout=10) as sock:
        with ctx.wrap_socket(sock, server_hostname=args.host) as tls:
            print("TLS OK", tls.version(), tls.cipher())
            tls.send(b"probe")
    print("done")


if __name__ == "__main__":
    main()
