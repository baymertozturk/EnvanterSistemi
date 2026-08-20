#!/bin/bash
set -e

echo "=== Creating microservice databases ==="

function create_database() {
    local database=$1
    echo "  Creating database: $database"
    psql -v ON_ERROR_STOP=0 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "CREATE DATABASE $database;" 2>/dev/null || true
}

create_database "orders_db"
create_database "inventory_db"
create_database "payments_db"
create_database "notifications_db"

echo "=== Database initialization complete ==="
