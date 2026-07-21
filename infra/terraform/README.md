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
                              # bucket + DynamoDB lock table every other stack uses.
                              # Local state (see bootstrap/README.md for why).
  environments/
    production/               # The only environment today (ADR-010). Root module —
                              # references child modules as they're added.
  modules/                    # Does not exist yet. Created module-by-module as each
                              # real story (network, cognito, database, ecs,
                              # api-gateway, observability) is implemented.
```

## Prerequisites

- **Terraform ~> 1.9** (pinned in every stack's `versions.tf`). Not installed on every
  contributor's machine is fine — validate via the official Docker image or via CI:

  ```bash
  docker run --rm -v "$PWD:/workspace" -w /workspace/infra/terraform hashicorp/terraform:1.9 \
    fmt -check -recursive .

  docker run --rm -v "$PWD:/workspace" -w /workspace/infra/terraform/bootstrap hashicorp/terraform:1.9 \
    sh -c "terraform init -backend=false && terraform validate"

  docker run --rm -v "$PWD:/workspace" -w /workspace/infra/terraform/environments/production hashicorp/terraform:1.9 \
    sh -c "terraform init -backend=false && terraform validate"
  ```

- **AWS provider ~> 5.60** (pinned in every stack's `versions.tf`).
- GitHub Actions authenticates via **OIDC** — no long-lived AWS access keys are stored as
  repository secrets (see `.github/workflows/infra-plan.yml`).

## Backend

`environments/production` uses a partial S3 backend configuration (`versions.tf`) — the
real bucket/table names are never hardcoded or committed. Copy
`environments/production/backend.hcl.example` to `backend.hcl` (gitignored) with the
values from `bootstrap`'s outputs, then:

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

## What this repository does NOT do (yet)

Per ADR-010 and TECH-137's explicit scope: no Terraform `apply` has been run against a
real AWS account, no Cognito/ECS/ALB/RDS/API Gateway/VPC/NAT Gateway resource exists, and
no AWS credential of any kind is stored in this repository. The GitHub Actions OIDC role
referenced by `infra-plan.yml`/`infra-apply.yml` does not exist yet either — it is a
documented prerequisite for the story that first needs `plan`/`apply` to run against real
AWS (TECH-130 or TECH-132, whichever lands first).
