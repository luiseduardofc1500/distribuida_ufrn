#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
# shellcheck source=env.sh
source "$SCRIPT_DIR/env.sh"

usage() {
  echo "Uso: $0 <serviceType> <instanceId> <port> [protocol]"
  echo "Exemplo HTTP: $0 isemail email-1 9101"
  echo "Exemplo UDP:  $0 isemail email-udp-1 9101 UDP"
}

if [ "$#" -lt 3 ] || [ "$#" -gt 4 ]; then
  usage
  exit 1
fi

SERVICE_TYPE="$1"
INSTANCE_ID="$2"
PORT="$3"
PROTOCOL="${4:-HTTP}"

require_built_classes

exec java \
  -Dapp.port="$PORT" \
  -Dapp.protocol="$PROTOCOL" \
  -Dapp.serviceType="$SERVICE_TYPE" \
  -Dapp.instanceId="$INSTANCE_ID" \
  -cp "$(get_app_classpath)" \
  br.ufrn.distribuida.App
