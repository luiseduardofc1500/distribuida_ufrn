#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
# shellcheck source=env.sh
source "$SCRIPT_DIR/env.sh"

require_built_classes

exec java -cp "$(get_app_classpath)" GatewayTCP
