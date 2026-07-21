# SIPSA — Terraform Infrastructure

Infrastructure-as-code for SIPSA's AWS resources, per
[ADR-010](../../docs/adr/ADR-010-aws-infrastructure-as-code.md) (Accepted, 2026-07-21).

## Status

**Scaffolding only (TECH-137).** No AWS resource has been created by this directory yet.
`bootstrap/` and `environments/production/` exist; no child module (network, cognito,
database, ecs, api-gateway, observability) does — each is added in its own reviewable
branch as the corresponding story (TECH-130/131/132) is implemented, per ADR-010's
Fase 0-5 sequence. Do not add an empty module directory ahead of the story that needs it.

## Structure

```
infra/terraform/
  bootstrap/                 # One-time, manually-applied. Creates the S3 state
                              # bucket every other stack's backend points to.
                              # Local state (see bootstrap/README.md for why).
  environments/
    production/               # The only environment today (ADR-010). Root module —
                              # references child modules as they're added.
  modules/                    # Does not exist yet. Created module-by-module as each
                              # real story (network, cognito, database, ecs,
                              # api-gateway, observability) is implemented.
```

## Toolchain versions

- **Terraform:** constrained to `>= 1.14.0, < 2.0.0` in every stack's `versions.tf` (S3
  native state locking, below, requires a modern client — this floor is deliberate, not
  arbitrary). CI and the commands below pin the concrete version currently validated:
  **Terraform 1.15.7**. Not installed on every contributor's machine is fine — validate
  via the official Docker image:

  ```bash
  docker run --rm -v "$PWD:/workspace" -w /workspace/infra/terraform \
    --entrypoint terraform hashicorp/terraform:1.15.7 fmt -check -recursive .

  docker run --rm -v "$PWD:/workspace" -w /workspace/infra/terraform/bootstrap \
    --entrypoint sh hashicorp/terraform:1.15.7 -c "terraform init -backend=false && terraform validate"

  docker run --rm -v "$PWD:/workspace" -w /workspace/infra/terraform/environments/production \
    --entrypoint sh hashicorp/terraform:1.15.7 -c "terraform init -backend=false && terraform validate"
  ```

- **AWS provider:** constrained to `>= 6.0.0, < 7.0.0`; the committed `.terraform.lock.hcl`
  files currently pin the concrete version resolved from that constraint,
  **`hashicorp/aws` 6.55.0**. Adopted at TECH-137 time deliberately — no AWS state or
  `apply` existed yet, so this was the correct moment to move onto the v6 major series
  rather than starting on v5 and migrating later. The [v6 upgrade
  guide](https://github.com/hashicorp/terraform-provider-aws/blob/main/website/docs/guides/version-6-upgrade.html.markdown)
  documents no breaking change affecting the resource types used in this repository
  (`aws_s3_bucket` and its companion resources) beyond a deprecated
  `s3_us_east_1_regional_endpoint = "legacy"` provider setting, which this repository
  never sets. `terraform validate` was re-confirmed clean against 6.55.0 for both stacks.
  Re-run `terraform init -upgrade -backend=false` and `terraform validate` (both stacks)
  whenever this constraint or lock file changes.

## Backend and state locking

`environments/production` uses a partial S3 backend configuration (`versions.tf`) with
**S3-native state locking** (`use_lockfile = true`) — **no DynamoDB lock table exists or
is created anywhere in this repository**. Current Terraform documentation marks the
DynamoDB-lock pattern as legacy in favor of this mechanism; see
`bootstrap/README.md` for the full rationale and the Terraform-version floor this
requires.

The real bucket/key/region are never hardcoded or committed. Copy
`environments/production/backend.hcl.example` to `backend.hcl` (gitignored) with the
bucket name from `bootstrap`'s output, then:

```bash
cd infra/terraform/environments/production
terraform init -backend-config=backend.hcl
```

See `bootstrap/README.md` for the full chicken-and-egg bootstrap strategy.

## Tagging convention

Every resource in every module must carry the common tags defined in
`environments/production/main.tf`'s `local.common_tags`: `Project`, `Environment`,
`Owner`, `ManagedBy`, `Repository`, `CostCenter` — applied automatically via each
provider's `default_tags` block, not per-resource.

## Security scanning

IaC misconfiguration scanning uses **Trivy** (`trivy config infra/terraform`) — `tfsec` is
not used anywhere in this repository (Aqua Security has folded tfsec's checks into Trivy;
running both would be redundant tooling for the same class of finding). Documented
exceptions use `# trivy:ignore:<AVD-ID>` comments placed directly above the resource block
the finding is on, each with an inline rationale — never a blanket suppression.

## GitHub Actions OIDC

CI authenticates to AWS via OIDC — no long-lived AWS access keys are stored as repository
secrets. See `.github/workflows/infra-plan.yml` for the workflow-side `permissions:
id-token: write` contract, and [ADR-010](../../docs/adr/ADR-010-aws-infrastructure-as-code.md)
for the IAM role trust-policy contract (subject claim format, per-purpose role
separation) that must exist on the AWS side before the `plan` job in that workflow does
anything beyond skip.

## What this repository does NOT do (yet)

Per ADR-010 and TECH-137's explicit scope: no Terraform `apply` has been run against a
real AWS account, no Cognito/ECS/ALB/RDS/API Gateway/VPC/NAT Gateway resource exists, and
no AWS credential of any kind is stored in this repository. The GitHub Actions OIDC roles
referenced by `infra-plan.yml`/`infra-apply.yml` do not exist yet either — creating them
is a prerequisite for whichever of TECH-130/TECH-132 first needs a real `plan`/`apply` to
run against AWS, not invented here.
