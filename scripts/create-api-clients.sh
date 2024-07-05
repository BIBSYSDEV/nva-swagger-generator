#!/bin/sh

set -e


mkdir -p openapi
mkdir -p generated

curl -u osteloff:osteloff https://swagger-ui-internal.sandbox.nva.aws.unit.no/docs/openapi.yaml -o openapi/sandbox-internal.yaml || true
curl -u osteloff:osteloff https://swagger-ui-internal.dev.nva.aws.unit.no/docs/openapi.yaml -o openapi/dev-internal.yaml || true
curl -u osteloff:osteloff https://swagger-ui-internal.test.nva.aws.unit.no/docs/openapi.yaml -o openapi/test-internal.yaml || true
curl -u osteloff:osteloff https://swagger-ui-internal.nva.unit.no/docs/openapi.yaml -o openapi/prod-internal.yaml || true
curl https://swagger-ui.sandbox.nva.aws.unit.no/docs/openapi.yaml -o openapi/sandbox-external.yaml || true
curl https://swagger-ui.dev.nva.aws.unit.no/docs/openapi.yaml -o openapi/dev-external.yaml || true
curl https://swagger-ui.test.nva.aws.unit.no/docs/openapi.yaml -o openapi/test-external.yaml || true
curl https://swagger-ui.nva.unit.no/docs/openapi.yaml -o openapi/prod-external.yaml || true

create_client() {
  env=$1
  lang=$2
  trap 'echo "create_client failed with parameters: $env $lang"' ERR
  #vacuum lint openapi/${env}.yaml -d -e -a -r nva-ruleset-recommended.yaml
  autorest --input-file=openapi/${env}.yaml --${lang} --output-folder=generated/${env}-${lang}
  trap - ERR
}

#create_client "sandbox-external" "csharp"
create_client "dev-external" "csharp"
create_client "test-external" "csharp"
create_client "prod-external" "csharp"

create_client "dev-external" "java"
create_client "test-external" "java"
create_client "prod-external" "java"

create_client "dev-internal" "csharp"
create_client "test-internal" "csharp"
create_client "prod-internal" "csharp"

create_client "dev-internal" "java"
create_client "test-internal" "java"
create_client "prod-internal" "java"