#!/usr/bin/env python3
"""PT-06: Rolling restart helper — drain via Actuator then wait for sessions."""
import argparse
import time

import requests


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--actuator-base", default="http://127.0.0.1:8080/actuator")
    p.add_argument("--timeout-sec", type=int, default=120)
    args = p.parse_args()

    r = requests.post(f"{args.actuator_base}/omnidrain", params={"timeoutSec": args.timeout_sec}, timeout=args.timeout_sec + 30)
    r.raise_for_status()
    print("drain", r.json())

    for _ in range(args.timeout_sec):
        lr = requests.get(f"{args.actuator_base}/omnilisteners", timeout=5)
        lr.raise_for_status()
        body = lr.json()
        if body.get("totalSessions", 1) == 0:
            print("sessions cleared")
            return
        time.sleep(1)
    raise SystemExit("sessions still present after timeout")


if __name__ == "__main__":
    main()
