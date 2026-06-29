#!/usr/bin/env bash
#
# Publish the per-service OpenAPI docs site (/services/) for ONE environment.
#
# InstallSwaggerUiHandler and GenerateServiceDocsHandler are scheduled (cron) and
# do NOT run on stack deploy, so after the swagger-generator stack deploys to an
# environment this script invokes them once to populate the site. Run it once per
# environment; the cron keeps the site fresh afterward.
#
# Usage:
#   scripts/publish-services-docs.sh <aws-profile>
#     e.g. scripts/publish-services-docs.sh nva-sandbox
#
#   Set DOCS_BASIC_AUTH=user:pass (as an env var) to enable the landing-page liveness check:
#     e.g. DOCS_BASIC_AUTH=username:password scripts/publish-services-docs.sh nva-prod

set -euo pipefail

profile="${1:-}"
if [ -z "$profile" ]; then
  echo "Usage: $0 <aws-profile>   (e.g. nva-sandbox)" >&2
  exit 2
fi

command -v aws >/dev/null 2>&1 || {
  echo "ERROR: aws CLI not found on PATH." >&2
  exit 1
}

# Single scratch file for Lambda responses, reused across invocations and removed on
# any exit (including a set -e abort mid-invoke), so error paths never leak it.
payload_file="$(mktemp)"
trap 'rm -f "$payload_file"' EXIT

invoke_handler() {
  local label="$1"
  local name_filter="$2"
  local function_name metadata payload

  function_name="$(aws lambda list-functions --profile "$profile" \
    --query "Functions[?contains(FunctionName, '$name_filter')].FunctionName" \
    --output text)"

  if [ -z "$function_name" ] || [ "$function_name" = "None" ]; then
    echo "ERROR: no Lambda matching '$name_filter' found in profile '$profile'." >&2
    exit 1
  fi
  case "$function_name" in
    *[[:space:]]*)
      echo "ERROR: '$name_filter' matched multiple functions:" >&2
      echo "  $function_name" >&2
      exit 1
      ;;
  esac

  echo "==> Invoking $label"
  echo "    $function_name"
  # --cli-read-timeout 0: the handlers can run longer than the default 60s socket timeout.
  metadata="$(aws lambda invoke --profile "$profile" \
    --function-name "$function_name" --cli-read-timeout 0 "$payload_file")"
  payload="$(cat "$payload_file")"

  case "$metadata" in
    *FunctionError*)
      echo "ERROR: $label reported a function error:" >&2
      echo "  ${payload:-$metadata}" >&2
      exit 1
      ;;
  esac

  if [ -n "$payload" ] && [ "$payload" != "null" ]; then
    echo "    OK, response: $payload"
  else
    echo "    OK"
  fi
}

# Resolve the internal docs host for an environment
docs_host_for_profile() {
  case "$1" in
    *e2e*) echo "swagger-ui-internal.e2e.nva.aws.unit.no" ;;
    *sandbox*) echo "swagger-ui-internal.sandbox.nva.aws.unit.no" ;;
    *dev*) echo "swagger-ui-internal.dev.nva.aws.unit.no" ;;
    *test*) echo "swagger-ui-internal.test.nva.aws.unit.no" ;;
    *prod*) echo "swagger-ui-internal.nva.unit.no" ;;
    *) echo "" ;;
  esac
}

# 1. Install/refresh swagger-ui assets at the root of both buckets (api.html depends on these).
invoke_handler "InstallSwaggerUiHandler" "InstallSwaggerUi"

# 2. Generate the per-service docs site under /services/ (landing + api.html + apis.json + specs).
invoke_handler "GenerateServiceDocsHandler" "GenerateService"

echo
host="$(docs_host_for_profile "$profile")"
docs_url="https://${host:-<internal-docs-host>}/services/index.html"
if [ -n "$host" ] && [ -n "${DOCS_BASIC_AUTH:-}" ]; then
  echo "==> Verifying $docs_url"
  status="$(curl -sS -u "$DOCS_BASIC_AUTH" \
    -o /dev/null -w '%{http_code}' "$docs_url" || echo "request-failed")"
  echo "    HTTP $status (expect 200; CloudFront may need a moment after invalidation)"
else
  echo "Done. Skipping liveness check (set DOCS_BASIC_AUTH=user:pass to enable it)."
  echo "Verify manually at $docs_url (behind basic auth)."
fi
