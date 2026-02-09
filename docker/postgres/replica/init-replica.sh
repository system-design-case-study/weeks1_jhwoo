#!/bin/bash
set -e

until pg_basebackup -h postgres-primary -D /var/lib/postgresql/data -U replicator -Fp -Xs -P -R; do
    echo "Waiting for primary to be ready..."
    sleep 2
done

cat >> /var/lib/postgresql/data/postgresql.auto.conf <<EOF
primary_conninfo = 'host=postgres-primary port=5432 user=replicator password=replicator_password application_name=${REPLICA_NAME}'
primary_slot_name = '${REPLICA_SLOT}'
EOF

touch /var/lib/postgresql/data/standby.signal

chown -R postgres:postgres /var/lib/postgresql/data
chmod 0700 /var/lib/postgresql/data
