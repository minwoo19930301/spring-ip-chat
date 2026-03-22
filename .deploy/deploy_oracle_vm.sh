#!/usr/bin/env bash
set -euo pipefail

BUNDLE_URL="${1:-}"
if [[ -z "$BUNDLE_URL" ]]; then
  echo "Usage: bash deploy_oracle_vm.sh <BUNDLE_URL>" >&2
  exit 1
fi

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "This script must run on Oracle Linux/Ubuntu VM shell, not on local macOS." >&2
  exit 1
fi

if [[ "${HOME:-}" == "/home/cloudshell" ]] || [[ "${HOSTNAME:-}" == *"cloudshell"* ]] || [[ -n "${OCI_CLOUD_SHELL:-}" ]] || [[ -n "${CLOUD_SHELL:-}" ]]; then
  echo "Detected OCI Cloud Shell. Run this script inside the target VM shell (opc/ubuntu), not Cloud Shell." >&2
  exit 1
fi

SUDO_CMD=()
if [[ "$(id -u)" -ne 0 ]]; then
  if [[ -x /usr/bin/sudo ]]; then
    SUDO_CMD=(sudo)
  else
    echo "No working sudo found. Run on the target VM shell with a sudo-capable user (opc/ubuntu)." >&2
    exit 1
  fi
fi

APP_DIR="/opt/spring-ip-chat"
TMP_DIR="/tmp/spring-ip-chat-deploy"
CURRENT_USER="${SUDO_USER:-${USER:-$(id -un)}}"
PRIMARY_GROUP="$(id -gn "$CURRENT_USER" 2>/dev/null || id -gn)"

if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | "${SUDO_CMD[@]}" sh
fi

"${SUDO_CMD[@]}" mkdir -p /opt
"${SUDO_CMD[@]}" chown "$CURRENT_USER:$PRIMARY_GROUP" /opt

rm -rf "$TMP_DIR"
mkdir -p "$TMP_DIR"
cd "$TMP_DIR"

curl -fL "$BUNDLE_URL" -o spring-ip-chat.tar.gz
rm -rf spring-ip-chat

tar -xzf spring-ip-chat.tar.gz

if [[ ! -d "$TMP_DIR/spring-ip-chat" ]]; then
  echo "bundle format is invalid" >&2
  exit 1
fi

rm -rf "$APP_DIR"
cp -R "$TMP_DIR/spring-ip-chat" "$APP_DIR"

cd "$APP_DIR"
if [[ ! -f .env ]]; then
  cp .env.always-on.example .env
fi

if ! grep -q '^POSTGRES_PASSWORD=' .env || grep -q '^POSTGRES_PASSWORD=change-this-to-a-strong-password' .env; then
  NEW_PW="$(openssl rand -hex 24)"
  if grep -q '^POSTGRES_PASSWORD=' .env; then
    sed -i.bak "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=${NEW_PW}/" .env
  else
    echo "POSTGRES_PASSWORD=${NEW_PW}" >> .env
  fi
  echo "Generated new POSTGRES_PASSWORD in $APP_DIR/.env"
fi

"${SUDO_CMD[@]}" docker compose -f docker-compose.always-on.yml up -d --build
"${SUDO_CMD[@]}" docker compose -f docker-compose.always-on.yml ps

echo "Deployment done. Try: http://$(curl -s ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')"
