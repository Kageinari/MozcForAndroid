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
ASSETS_DIR="${REPO_ROOT}/app/src/main/assets"
TARGET_DATA="${ASSETS_DIR}/mozc.data"
GH_API="https://api.github.com"

usage() {
  cat <<EOF
Usage: $(basename "$0") <command> [options]

Commands:
  install       Install libmozc.so and mozc.data (default if no command given)
  verify        Exit 0 when libmozc.so and mozc.data exist

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

extract_native_artifacts() {
  local zip_path="$1"
  python3 - "$zip_path" "$MOZC_ARTIFACT" "$TARGET_SO" "$TARGET_DATA" <<'PY'
import io
import pathlib
import sys
import zipfile

zip_path = sys.argv[1]
artifact_name = sys.argv[2]
target_so = pathlib.Path(sys.argv[3])
target_data = pathlib.Path(sys.argv[4])
so_path = "libs/arm64-v8a/libmozc.so"
data_path = "data/mozc.data"


def open_native_archive(path: str) -> zipfile.ZipFile:
    archive = zipfile.ZipFile(path)
    if so_path in archive.namelist():
        return archive
    for name in archive.namelist():
        if name.rstrip("/").rsplit("/", 1)[-1] == artifact_name:
            return zipfile.ZipFile(io.BytesIO(archive.read(name)))
    raise KeyError(so_path)


def install_member(archive: zipfile.ZipFile, member: str, dest: pathlib.Path) -> None:
    if member not in archive.namelist():
        raise KeyError(member)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(archive.read(member))


try:
    with open_native_archive(zip_path) as archive:
        install_member(archive, so_path, target_so)
        try:
            install_member(archive, data_path, target_data)
        except KeyError:
            if target_data.exists():
                target_data.unlink()
            print(f"WARNING: {data_path} not found in {zip_path}; mozc.data was not installed",
                  file=sys.stderr)
except (KeyError, zipfile.BadZipFile) as exc:
    print(f"ERROR: native artifacts not found in {zip_path} (or nested {artifact_name}): {exc}",
          file=sys.stderr)
    raise SystemExit(1) from exc

print(f"Installed {target_so.stat().st_size} bytes to {target_so}")
if target_data.exists():
    print(f"Installed {target_data.stat().st_size} bytes to {target_data}")
PY
}

install_from_zip() {
  local zip_path="$1"
  if [[ ! -f "$zip_path" ]]; then
    echo "ERROR: zip not found: $zip_path" >&2
    exit 1
  fi
  extract_native_artifacts "$zip_path"
  chmod 644 "$TARGET_SO"
  if [[ -f "$TARGET_DATA" ]]; then
    chmod 644 "$TARGET_DATA"
  fi
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
  if ! gh_api_download \
    "${GH_API}/repos/${MOZC_REPO}/actions/artifacts/${artifact_id}/zip" \
    "$tmp_zip"; then
    rm -f "$tmp_zip"
    echo "ERROR: failed to download ${MOZC_ARTIFACT} from ${MOZC_REPO} (run ${run_id})" >&2
    echo "Check GH_TOKEN has read access to ${MOZC_REPO} Actions artifacts." >&2
    exit 1
  fi

  echo "Downloaded ${MOZC_ARTIFACT} from ${MOZC_REPO} run ${run_id}"
  install_from_zip "$tmp_zip"
  rm -f "$tmp_zip"
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

  if [[ $force -eq 0 && -f "$TARGET_SO" && -f "$TARGET_DATA" ]]; then
    echo "libmozc.so and mozc.data already present"
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
  local ok=1
  if [[ -f "$TARGET_SO" ]]; then
    echo "OK: $TARGET_SO"
  else
    echo "ERROR: missing $TARGET_SO" >&2
    ok=0
  fi
  if [[ -f "$TARGET_DATA" ]]; then
    echo "OK: $TARGET_DATA"
  else
    echo "ERROR: missing $TARGET_DATA" >&2
    ok=0
  fi
  if [[ $ok -eq 1 ]]; then
    exit 0
  fi
  echo "Run: scripts/fetch_native_libs.sh install --artifact" >&2
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