#!/usr/bin/env python3
"""PT-03: Downlink routing — unified topic + router or per-node topic."""
import argparse
import json
import time
import uuid

from kafka import KafkaConsumer, KafkaProducer


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--bootstrap", default="127.0.0.1:19092")
    p.add_argument("--device-id", required=True)
    p.add_argument("--node-id", default="local-8080")
    p.add_argument("--use-router", action="store_true", help="publish to omni.command.downlink")
    args = p.parse_args()

    topic = "omni.command.downlink" if args.use_router else f"omni.command.downlink.{args.node_id}"
    message_id = str(uuid.uuid4())
    cmd = {
        "messageId": message_id,
        "deviceId": args.device_id,
        "protocol": "simple-frame",
        "commandType": "setParam",
        "payload": {"k": 1},
        "timeoutMs": 5000,
    }
    producer = KafkaProducer(
        bootstrap_servers=args.bootstrap,
        value_serializer=lambda v: json.dumps(v).encode(),
        key_serializer=lambda k: k.encode(),
    )
    producer.send(topic, key=args.device_id, value=cmd)
    producer.flush()

    consumer = KafkaConsumer(
        "omni.command.downlink.result",
        bootstrap_servers=args.bootstrap,
        auto_offset_reset="latest",
        consumer_timeout_ms=15000,
        value_deserializer=lambda v: json.loads(v.decode()),
    )
    t0 = time.time()
    for msg in consumer:
        if msg.value.get("messageId") == message_id:
            print("result", msg.value)
            return
        if time.time() - t0 > 15:
            break
    raise SystemExit("timeout waiting for downlink result")


if __name__ == "__main__":
    main()
