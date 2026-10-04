#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="/home/admin/energy-deploy"

set -a
# shellcheck disable=SC1091
source "${DEPLOY_DIR}/env.sh"
set +a

mkdir -p "${DEPLOY_DIR}/logs" /home/admin/energy-management-uploads

exec /usr/bin/java -XX:-CreateCoredumpOnCrash \
  -jar "${DEPLOY_DIR}/energy-management.war" \
  --server.port=18099 \
  --server.servlet.context-path=/
