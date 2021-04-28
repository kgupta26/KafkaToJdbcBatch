#!/bin/bash

HEADER="Content-Type: application/json"
DATA=$( cat << EOF
{
  "name": "data-gen",
  "config": {
      "connector.class": "io.confluent.kafka.connect.datagen.DatagenConnector",
      "kafka.topic": "topic2",
      "quickstart": "users",
      "key.converter": "org.apache.kafka.connect.storage.StringConverter",
      "value.converter": "org.apache.kafka.connect.json.JsonConverter",
      "value.converter.schemas.enable": "false",
      "max.interval": "1000",
      "iterations": "10000000",
      "tasks.max": "1"
  }
}
EOF
)

curl -X POST -H "${HEADER}" --data "${DATA}" http://localhost:8083/connectors