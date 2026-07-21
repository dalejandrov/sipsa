# Terraform Bootstrap Stack

Creates the S3 bucket that every other Terraform stack in this repository (starting with
`environments/production`) uses as its remote backend.

## State Locking: S3-Native, Not DynamoDB

Every other stack's backend uses Terraform's native S3 lockfile mechanism
(`use_lockfile = true` in `environments/production/versions.tf`), not a DynamoDB lock
table. This bootstrap stack therefore provisions **only an S3 bucket** — no locking
resource of any kind.

**Why not DynamoDB:** current Terraform documentation marks the DynamoDB-based locking
pattern as legacy in favor of the S3-native lockfile, and no consuming stack exists yet
that would need the DynamoDB pattern for compatibility. Provisioning a DynamoDB table for
a backend that doesn't exist yet, on the strength of a locking mechanism current guidance
already treats as superseded, would be adding infrastructure this repository would need
to un-provision shortly after.

**Requirement this creates:** `use_lockfile` needs a modern Terraform client. Every stack
in this repository pins `required_version = ">= 1.14.0, < 2.0.0"` accordingly (see
`versions.tf` in both this stack and `environments/production`) — there is **no
deliberate support for older Terraform clients that only know the DynamoDB-lock
pattern**. If a contributor's local Terraform predates S3-native locking, the fix is to
upgrade Terraform, not to reintroduce DynamoDB.

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

Once this stack has been applied once, note its `state_bucket_name` output and supply it,
along with a fixed `key` and `region`, to `environments/production`'s backend
configuration — see `environments/production/backend.hcl.example` for the exact keys
expected. `use_lockfile = true` is already declared in that stack's `versions.tf` — it
does not go in `backend.hcl`.

This stack's own local `terraform.tfstate` file must be preserved (e.g., encrypted and
stored outside this repository, or migrated to the bucket it created, once that bucket
exists) — it is the only record of the bootstrap resources' Terraform-managed state.
