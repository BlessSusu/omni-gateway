#!/usr/bin/env bash
# Linux/macOS：创建 OmniGateway 所需 Kafka Topic

set -euo pipefail
BOOTSTRAP="${BOOTSTRAP:-localhost:9092}"
CONTAINER="${KAFKA_CONTAINER:-omni-kafka}"

topics=(
  omni.device.uplink
  omni.device.lifecycle
  omni.command.downlink
  omni.command.downlink.result
)

echo "Creating topics on ${BOOTSTRAP} ..."
for t in "${topics[@]}"; do
  docker exec "${CONTAINER}" /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${t}" \
    --partitions 3 \
    --replication-factor 1
  echo "  OK: ${t}"
done

echo ""
docker exec "${CONTAINER}" /opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP}" --list
