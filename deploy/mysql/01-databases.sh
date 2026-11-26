#!/bin/bash
set -euo pipefail
for role in user market prediction scheduler notification; do
  key="${role^^}_DB_PASSWORD"
  credential="${!key}"
  if [[ ! "$credential" =~ ^[A-Za-z0-9]{32,128}$ ]]; then
    echo "Invalid local database credential format for $role" >&2
    exit 1
  fi
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root <<SQL
CREATE DATABASE IF NOT EXISTS stock_${role} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'stock_${role}'@'%' IDENTIFIED BY '${credential}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON stock_${role}.* TO 'stock_${role}'@'%';
SQL
done
