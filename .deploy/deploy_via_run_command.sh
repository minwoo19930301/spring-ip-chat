#!/usr/bin/env bash
set -euo pipefail

OCI_BIN="${OCI_BIN:-oci}"

INSTANCE_ID="${1:-${OCI_INSTANCE_ID:-}}"
COMPARTMENT_ID="${2:-${OCI_COMPARTMENT_ID:-}}"
BUNDLE_URL="${3:-${BUNDLE_URL:-}}"
SCRIPT_URL="${4:-${SCRIPT_URL:-}}"

DEFAULT_BUNDLE_URL="https://objectstorage.ap-chuncheon-1.oraclecloud.com/p/DPRVtnpQDGJ74bw1AVR9lip_iFxwOq3PoiZU6j6kGfQDwAiF9YaL56pISCDPkmOt/n/axkxo9qulmk6/b/spring-ip-chat-deploy/o/spring-ip-chat-20260313T162253Z.tar.gz"
DEFAULT_SCRIPT_URL="https://objectstorage.ap-chuncheon-1.oraclecloud.com/p/zjuy1HhDNKmCZ3Gp0Z3y4YKMbGsCkn_53O_EMciYfV_Ji5fhjBwNzZMaaU1-qF12/n/axkxo9qulmk6/b/spring-ip-chat-deploy/o/deploy_oracle_vm_20260313T165121Z.sh"

if [[ -z "${INSTANCE_ID}" || -z "${COMPARTMENT_ID}" ]]; then
  echo "Usage: bash deploy_via_run_command.sh <INSTANCE_OCID> <COMPARTMENT_OCID> [BUNDLE_URL] [DEPLOY_SCRIPT_URL]" >&2
  exit 1
fi

if ! command -v "${OCI_BIN}" >/dev/null 2>&1; then
  echo "OCI CLI not found. Run this in OCI Cloud Shell or install oci-cli." >&2
  exit 1
fi

if [[ -z "${BUNDLE_URL}" ]]; then
  BUNDLE_URL="${DEFAULT_BUNDLE_URL}"
fi
if [[ -z "${SCRIPT_URL}" ]]; then
  SCRIPT_URL="${DEFAULT_SCRIPT_URL}"
fi

TENANCY_ID="${OCI_TENANCY_ID:-$(awk -F= '/^tenancy=/{print $2}' "${HOME}/.oci/config" 2>/dev/null || true)}"
DG_NAME="${OCI_RUN_COMMAND_DG_NAME:-spring-ip-chat-run-command-dg}"
POLICY_NAME="${OCI_RUN_COMMAND_POLICY_NAME:-spring-ip-chat-run-command-policy}"

if [[ -z "${TENANCY_ID}" ]]; then
  echo "TENANCY OCID not found. Set OCI_TENANCY_ID or configure ~/.oci/config." >&2
  exit 1
fi

tmpdir=""
workdir=""
cleanup() {
  [[ -n "${tmpdir}" ]] && rm -rf "${tmpdir}"
  [[ -n "${workdir}" ]] && rm -rf "${workdir}"
}
trap cleanup EXIT

if [[ "${SKIP_IAM:-0}" != "1" ]]; then
  MATCHING_RULE="ALL {instance.id = '${INSTANCE_ID}'}"

  tmpdir="$(mktemp -d)"

  dg_json="${tmpdir}/dynamic-groups.json"
  policy_json="${tmpdir}/policies.json"
  statements_json="${tmpdir}/statements.json"

  "${OCI_BIN}" iam dynamic-group list --all --compartment-id "${TENANCY_ID}" > "${dg_json}"
  "${OCI_BIN}" iam policy list --all --compartment-id "${TENANCY_ID}" > "${policy_json}"

  EXISTING_DG_ID="$(python3 - "${dg_json}" "${DG_NAME}" <<'PY'
import json
import sys

path, target = sys.argv[1], sys.argv[2]
data = json.load(open(path))
for item in data.get("data", []):
    if item.get("name") == target:
        print(item["id"])
        break
PY
)"

  if [[ -z "${EXISTING_DG_ID}" ]]; then
    "${OCI_BIN}" iam dynamic-group create \
      --compartment-id "${TENANCY_ID}" \
      --name "${DG_NAME}" \
      --description "Allows the instance to use OCI Run Command" \
      --matching-rule "${MATCHING_RULE}" \
      --wait-for-state ACTIVE >/dev/null
  else
    "${OCI_BIN}" iam dynamic-group update \
      --dynamic-group-id "${EXISTING_DG_ID}" \
      --description "Allows the instance to use OCI Run Command" \
      --matching-rule "${MATCHING_RULE}" \
      --force \
      --wait-for-state ACTIVE >/dev/null
  fi

  python3 - "${DG_NAME}" > "${statements_json}" <<'PY'
import json
import sys

name = sys.argv[1]
statements = [
    f"Allow dynamic-group {name} to manage instance-family in tenancy",
    f"Allow dynamic-group {name} to use instance-agent-command-family in tenancy",
    f"Allow dynamic-group {name} to use instance-agent-command-execution-family in tenancy where request.instance.id = target.instance.id",
]
print(json.dumps(statements))
PY

  EXISTING_POLICY_ID="$(python3 - "${policy_json}" "${POLICY_NAME}" <<'PY'
import json
import sys

path, target = sys.argv[1], sys.argv[2]
data = json.load(open(path))
for item in data.get("data", []):
    if item.get("name") == target:
        print(item["id"])
        break
PY
)"

  if [[ -z "${EXISTING_POLICY_ID}" ]]; then
    "${OCI_BIN}" iam policy create \
      --compartment-id "${TENANCY_ID}" \
      --name "${POLICY_NAME}" \
      --description "Policies for OCI Run Command" \
      --statements "file://${statements_json}" \
      --wait-for-state ACTIVE >/dev/null
  else
    "${OCI_BIN}" iam policy update \
      --policy-id "${EXISTING_POLICY_ID}" \
      --description "Policies for OCI Run Command" \
      --statements "file://${statements_json}" \
      --force \
      --wait-for-state ACTIVE >/dev/null
  fi
fi

workdir="$(mktemp -d)"

content_json="${workdir}/content.json"
target_json="${workdir}/target.json"

python3 - <<PY > "${content_json}"
import json

script = """#!/usr/bin/env bash
set -euo pipefail
curl -fsSL "${SCRIPT_URL}" -o /tmp/deploy_oracle_vm.sh
bash /tmp/deploy_oracle_vm.sh "${BUNDLE_URL}"
"""

content = {
  "source": {
    "sourceType": "TEXT",
    "text": script
  },
  "output": {
    "outputType": "TEXT"
  }
}
print(json.dumps(content))
PY

cat > "${target_json}" <<EOF
{"instanceId":"${INSTANCE_ID}"}
EOF

command_id="$("${OCI_BIN}" instance-agent command create \
  --compartment-id "${COMPARTMENT_ID}" \
  --content "file://${content_json}" \
  --target "file://${target_json}" \
  --timeout-in-seconds 1800 \
  --display-name "spring-ip-chat-deploy" \
  --query "data.id" --raw-output)"

echo "Run Command issued: ${command_id}"

poll_interval=10
max_wait=900
elapsed=0
while true; do
  state="$("${OCI_BIN}" instance-agent command-execution get \
    --command-id "${command_id}" \
    --instance-id "${INSTANCE_ID}" \
    --query "data.lifecycleState" --raw-output)"
  echo "Run Command state: ${state}"

  case "${state}" in
    SUCCEEDED)
      break
      ;;
    FAILED|TIMED_OUT|CANCELED)
      echo "Run Command failed with state: ${state}" >&2
      "${OCI_BIN}" instance-agent command-execution get \
        --command-id "${command_id}" \
        --instance-id "${INSTANCE_ID}"
      exit 1
      ;;
  esac

  if [[ "${elapsed}" -ge "${max_wait}" ]]; then
    echo "Run Command timed out after ${max_wait}s. Check the execution state manually." >&2
    "${OCI_BIN}" instance-agent command-execution get \
      --command-id "${command_id}" \
      --instance-id "${INSTANCE_ID}"
    exit 1
  fi

  sleep "${poll_interval}"
  elapsed=$((elapsed + poll_interval))
done

"${OCI_BIN}" instance-agent command-execution get \
  --command-id "${command_id}" \
  --instance-id "${INSTANCE_ID}"

echo "If the app doesn't respond, confirm VCN security list allows inbound TCP/80."
