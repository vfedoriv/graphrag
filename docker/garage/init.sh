#!/bin/sh
set -eu

: "${LANGFUSE_GARAGE_ACCESS_KEY:?LANGFUSE_GARAGE_ACCESS_KEY is required}"
: "${LANGFUSE_GARAGE_SECRET_KEY:?LANGFUSE_GARAGE_SECRET_KEY is required}"

bucket="${LANGFUSE_GARAGE_BUCKET:-langfuse}"
key_name="${LANGFUSE_GARAGE_KEY_NAME:-langfuse-local}"
node_zone="${LANGFUSE_GARAGE_NODE_ZONE:-local}"
node_capacity="${LANGFUSE_GARAGE_NODE_CAPACITY:-10G}"

attempt=0
until status="$(garage status 2>/dev/null)"; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    echo "Garage did not become reachable within 60 seconds" >&2
    exit 1
  fi
  sleep 1
done

if printf '%s\n' "$status" | grep -q 'NO ROLE ASSIGNED'; then
  node_id="$(printf '%s\n' "$status" | awk '/NO ROLE ASSIGNED/ { print $1; exit }')"
  if [ -z "$node_id" ]; then
    echo "Unable to determine the unassigned Garage node ID" >&2
    exit 1
  fi
  garage layout assign --zone "$node_zone" --capacity "$node_capacity" "$node_id"
  garage layout apply --version 1
fi

if ! garage bucket info "$bucket" >/dev/null 2>&1; then
  garage bucket create "$bucket"
fi

if ! garage key info "$LANGFUSE_GARAGE_ACCESS_KEY" >/dev/null 2>&1; then
  garage key import --yes -n "$key_name" \
    "$LANGFUSE_GARAGE_ACCESS_KEY" "$LANGFUSE_GARAGE_SECRET_KEY"
fi

garage bucket allow --read --write "$bucket" --key "$LANGFUSE_GARAGE_ACCESS_KEY"
garage bucket info "$bucket"
