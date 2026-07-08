# nva-swagger-generator

Fetches the OpenAPI (Swagger) documentation for NVA's services and publishes it as Swagger UI sites on S3 + CloudFront.

It produces two kinds of output:

- **Combined docs**: all services merged into a single spec, in two variants.
  - **external** (public): only operations tagged `external`.
  - **internal** (HTTP basic auth): all operations.
- **Per-service docs** (`/services/`): each service's source OpenAPI files rendered verbatim, so all examples and descriptions are preserved (no merging).
  A landing page lists every service, and each links to a Swagger UI page with a spec picker.
  Served on the internal site only, side by side with the combined docs.

## Where to see the published docs

`<env>` is one of `dev`, `test`, `sandbox`.

| Site                   | Non-prod                                                      | Prod                                                |
| ---------------------- | ------------------------------------------------------------- | --------------------------------------------------- |
| External (public)      | `https://swagger-ui.<env>.nva.aws.unit.no/`                   | `https://swagger-ui.nva.unit.no/`                   |
| Internal (basic auth)  | `https://swagger-ui-internal.<env>.nva.aws.unit.no/`          | `https://swagger-ui-internal.nva.unit.no/`          |
| Per-service (internal) | `https://swagger-ui-internal.<env>.nva.aws.unit.no/services/` | `https://swagger-ui-internal.nva.unit.no/services/` |

## How it works

Inputs:

- Each service's source OpenAPI files are uploaded to the `OpenApiDocsBucket` S3 bucket by the infra master pipeline's `CopyDocs` Lambda, on every service deploy.
- Swagger UI static assets are downloaded from GitHub by `InstallSwaggerUiHandler`.

Lambda handlers (scheduled, no API Gateway trigger):

- `GenerateExternalDocsHandler`: builds the combined external spec, writes it to the external bucket, invalidates CloudFront.
- `GenerateInternalDocsHandler`: builds the combined internal spec (all operations) plus per-API YAML, writes to the internal bucket.
- `GenerateServiceDocsHandler`: renders each source spec verbatim under `/services/` on the internal bucket (landing page, `api.html` spec picker, `apis.json`, and the specs).
- `PublishDocumentationsHandler`: manages API Gateway documentation versions.
- `InstallSwaggerUiHandler`: installs the Swagger UI assets into both buckets (no schedule; invoked manually or by the post-deploy script below).

## Deploy

This assumes you have configured AWS CLI with an SSO session named `sikt` and account profiles named `nva-<environment>`.

Deployment is via AWS SAM (`template.yaml`), built and shipped by the service's CodePipeline (`buildspec.yaml`) as part of the NVA master pipeline.

The doc-generating handlers are scheduled and do not run on deploy, so after the stack deploys to an environment, populate the per-service site once:

```sh
# Log in once per session (the SSO token is reused across profiles under the `sikt` session):
aws sso login --sso-session sikt

# Initialize the per service site in each environment:
scripts/publish-services-docs.sh nva-sandbox   # repeat per env (nva-e2e, nva-dev, nva-test, nva-prod)

# Optionally, pass the username/password to verify the landing page is live:
DOCS_BASIC_AUTH=user:pass scripts/publish-services-docs.sh nva-sandbox
```
