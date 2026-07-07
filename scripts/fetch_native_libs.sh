#!/usr/bin/env bash
# Install libmozc.so into app/src/main/jniLibs from a mozc native_libs.zip.
# Binaries are not stored in this repository (see .gitignore).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=mozc_native.env
source "$SCRIPT_DIR/mozc_native.env"

JNI_ARM64="${REPO_ROOT}/app/src/main/jniLibs/arm64-v8a"
TARGET_SO="${JNI_ARM64}/libmozc.so"
GH_API="https://api.github.com"

usage() {
  cat <<EOF
Usage: $(basename "$0") <command> [options]

Commands:
  install       Install libmozc.so into jniLibs (default if no command given)
  verify        Exit 0 when libmozc.so exists

Options (install):
  --zip PATH    Extract from a local native_libs.zip
  --artifact    Download native_libs.zip from mozc GitHub Actions (needs curl + GH_TOKEN)
  --force       Reinstall even when libmozc.so already exists

Environment:
  NATIVE_LIBS_ZIP   Path to native_libs.zip (same as --zip)
  GH_TOKEN          GitHub token with access to ${MOZC_REPO} artifacts
  MOZC_REPO / MOZC_REF / MOZC_WORKFLOW / MOZC_ARTIFACT / MOZC_NATIVE_RUN_ID
                    (see mozc_native.env)
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: $1 is required for --artifact" >&2
    exit 1
  fi
}

gh_api_get() {
  local url="$1"
  curl -fsSL \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer ${GH_TOKEN}" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "$url"
}

gh_api_download() {
  local url="$1"
  local dest="$2"
  curl -fsSL \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer ${GH_TOKEN}" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    -o "$dest" \
    "$url"
}

resolve_run_id() {
  if [[ -n "${MOZC_NATIVE_RUN_ID:-}" ]]; then
    echo "$MOZC_NATIVE_RUN_ID"
    return 0
  fi

  local workflow="${MOZC_WORKFLOW_ID:-$MOZC_WORKFLOW}"
  local runs_json
  runs_json="$(
    gh_api_get \
      "${GH_API}/repos/${MOZC_REPO}/actions/workflows/${workflow}/runs?branch=master&status=completed&per_page=30"
  )"

  printf '%s' "$runs_json" | python3 -c '
import json
import sys

ref = sys.argv[1]
data = json.load(sys.stdin)
for run in data.get("workflow_runs", []):
    if run.get("conclusion") != "success":
        continue
    head_sha = run.get("head_sha", "")
    if ref and head_sha != ref:
        continue
    print(run["id"])
    break
' "$MOZC_REF"
}

resolve_artifact_id() {
  local run_id="$1"
  local artifacts_json
  artifacts_json="$(gh_api_get "${GH_API}/repos/${MOZC_REPO}/actions/runs/${run_id}/artifacts")"

  printf '%s' "$artifacts_json" | python3 -c '
import json
import sys

artifact_name = sys.argv[1]
data = json.load(sys.stdin)
for artifact in data.get("artifacts", []):
    if artifact.get("name") == artifact_name and not artifact.get("expired", False):
        print(artifact["id"])
        break
' "$MOZC_ARTIFACT"
}

install_from_zip() {
  local zip_path="$1"
  if [[ ! -f "$zip_path" ]]; then
    echo "ERROR: zip not found: $zip_path" >&2
    exit 1
  fi
  mkdir -p "$JNI_ARM64"
  unzip -p "$zip_path" "libs/arm64-v8a/libmozc.so" > "$TARGET_SO"
  chmod 644 "$TARGET_SO"
  echo "Installed $(wc -c < "$TARGET_SO") bytes to $TARGET_SO"
}

download_artifact() {
  require_cmd curl
  require_cmd python3
  require_cmd unzip

  if [[ -z "${GH_TOKEN:-}" ]]; then
    echo "ERROR: GH_TOKEN is required to download artifacts from ${MOZC_REPO}" >&2
    echo "Set MOZC_ARTIFACT_TOKEN in repository secrets (CI) or export GH_TOKEN locally." >&2
    exit 1
  fi

  local run_id artifact_id tmp_zip
  if ! run_id="$(resolve_run_id)"; then
    echo "ERROR: failed to query workflow runs for ${MOZC_REPO}" >&2
    exit 1
  fi
  if [[ -z "$run_id" ]]; then
    echo "ERROR: no successful ${MOZC_WORKFLOW} run found on ${MOZC_REPO}" >&2
    if [[ -n "${MOZC_REF:-}" ]]; then
      echo "Expected head_sha=${MOZC_REF}. Update MOZC_NATIVE_RUN_ID in mozc_native.env." >&2
    fi
    exit 1
  fi

  if ! artifact_id="$(resolve_artifact_id "$run_id")"; then
    echo "ERROR: failed to list artifacts for run ${run_id}" >&2
    exit 1
  fi
  if [[ -z "$artifact_id" ]]; then
    echo "ERROR: artifact ${MOZC_ARTIFACT} not found in run ${run_id}" >&2
    exit 1
  fi

  tmp_zip="$(mktemp /tmp/native_libs.XXXXXX.zip)"
  trap 'rm -f "$tmp_zip"' RETURN
  if ! gh_api_download \
    "${GH_API}/repos/${MOZC_REPO}/actions/artifacts/${artifact_id}/zip" \
    "$tmp_zip"; then
    echo "ERROR: failed to download ${MOZC_ARTIFACT} from ${MOZC_REPO} (run ${run_id})" >&2
    echo "Check GH_TOKEN has read access to ${MOZC_REPO} Actions artifacts." >&2
    exit 1
  fi

  echo "Downloaded ${MOZC_ARTIFACT} from ${MOZC_REPO} run ${run_id}"
  install_from_zip "$tmp_zip"
}

cmd_install() {
  local force=0
  local mode="zip"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --zip)
        NATIVE_LIBS_ZIP="$2"
        shift 2
        ;;
      --artifact)
        mode="artifact"
        shift
        ;;
      --force)
        force=1
        shift
        ;;
      *)
        echo "ERROR: unknown option: $1" >&2
        usage >&2
        exit 1
        ;;
    esac
  done

  if [[ $force -eq 0 && -f "$TARGET_SO" ]]; then
    echo "libmozc.so already present at $TARGET_SO"
    exit 0
  fi

  if [[ -n "${NATIVE_LIBS_ZIP:-}" ]]; then
    install_from_zip "$NATIVE_LIBS_ZIP"
  elif [[ "$mode" == "artifact" ]]; then
    download_artifact
  else
    echo "ERROR: provide --zip PATH, set NATIVE_LIBS_ZIP, or use --artifact" >&2
    usage >&2
    exit 1
  fi
}

cmd_verify() {
  if [[ -f "$TARGET_SO" ]]; then
    echo "OK: $TARGET_SO"
    exit 0
  fi
  echo "ERROR: missing $TARGET_SO — run: scripts/fetch_native_libs.sh install --artifact" >&2
  exit 1
}

main() {
  local cmd="${1:-install}"
  if [[ $# -gt 0 ]]; then
    shift
  fi
  case "$cmd" in
    install) cmd_install "$@" ;;
    verify) cmd_verify ;;
    -h|--help|help) usage ;;
    *)
      echo "ERROR: unknown command: $cmd" >&2
      usage >&2
      exit 1
      ;;
  esac
}

main "$@"