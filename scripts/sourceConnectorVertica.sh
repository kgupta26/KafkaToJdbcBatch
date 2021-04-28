#!/bin/bash

HEADER="Content-Type: application/json"
DATA=$( cat << EOF
{
  "name": "vertica-source",
  "config": {
        "name": "api_swat_poc_cx_agreements_source",
        "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector",
        "tasks.max": "1",
        "connection.url": "jdbc:vertica://vertica-edw-dev.dsawsnprd.massmutual.com:443/advana?TrustStorePath=/mm-cert-bundle.jks&TrustStorePassword=<JKS PASSWORD>&SSL=true",
        "connection.user": "<USERNAME>",
        "connection.password": "<PASSWORD>",
        "table.whitelist": "my_table",
        "dialect.name": "VerticaDatabaseDialect",
        "mode": "timestamp",
        "timestamp.column.name": "DATE_LAST_CREATED_IN_VERTICA",    
        "topic.prefix": "mm.poc.sourcetopic.",
        "validate.non.null" : "false"
    }
}
EOF


curl -X POST -H "${HEADER}" --data "${DATA}" http://localhost:8083/connectors