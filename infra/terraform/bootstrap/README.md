# Terraform Bootstrap Stack

Creates the S3 bucket and DynamoDB lock table that every other Terraform stack in this
repository (starting with `environments/production`) uses as its remote backend.

## The Chicken-and-Egg Problem

Terraform's own remote state needs an S3 bucket to live in, but that bucket has to be
created by *something* — and if that something is itself a Terraform stack using a remote
backend, the backend doesn't exist yet to store its own creation in.

Three ways to resolve this were considered:

1. **A separate bootstrap stack with local state** (chosen here). This stack's own state
   is a local `terraform.tfstate` file, applied manually, rarely (only when the backend
   itself changes — bucket rename, region move, encryption policy change). It is
   deliberately the one exception to "no local state" in this repository.
2. **Create the bucket manually** (console or a one-off AWS CLI command), never
   Terraform-managed. Rejected: loses reviewability and drift detection for a resource
   that, while rarely changed, is still infrastructure and should be defined as code like
   everything else.
3. **Start with local state everywhere and migrate later.** Rejected: every other stack
   would need a state-migration step later, and it's simpler to never have that problem
   for the stacks that matter (network, compute, identity, gateway) by solving it once,
   here.

## One-Time Manual Bootstrap Process

This stack is **not** run by CI. It is applied once, manually, by whoever owns AWS
infrastructure for this project (see ADR-010 — the repository owner, initially), before
`environments/production` can `terraform init` against a real backend.

```bash
cd infra/terraform/bootstrap

# Choose a globally-unique bucket name first — S3 bucket names are unique across
# ALL of AWS, not just this account.
terraform init
terraform plan \
  -var="state_bucket_name=sipsa-terraform-state-<choose-a-unique-suffix>" \
  -var="owner=<repository owner>" \
  -var="cost_center=<real cost center, once known>"

# Review the plan. Only after review:
# terraform apply -var="state_bucket_name=..." -var="owner=..." -var="cost_center=..."
```

**No `apply` has been run against a real AWS account as part of this story (TECH-137).**
This README documents the process for when the repository owner is ready to run it.

## After Bootstrapping

Once this stack has been applied once, note its outputs (`state_bucket_name`,
`lock_table_name`) and supply them to `environments/production`'s backend configuration
— see `environments/production/backend.hcl.example` for the exact keys expected.

This stack's own local `terraform.tfstate` file must be preserved (e.g., encrypted and
stored outside this repository, or migrated to the bucket it created, once that bucket
exists) — it is the only record of the bootstrap resources' Terraform-managed state.
