terraform {
  # S3-native state locking (use_lockfile, below) requires a modern Terraform
  # client — this constraint is deliberately not widened to support older
  # clients that only know the DynamoDB-lock pattern this repository no
  # longer uses. See ../../bootstrap/README.md.
  required_version = ">= 1.14.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 6.0.0, < 7.0.0"
    }
  }

  # Partial backend configuration: use_lockfile is a literal (not
  # environment-specific) so it lives here, in version control. The real
  # bucket/key/region come from the bootstrap stack's outputs and are
  # supplied at `terraform init` time via `-backend-config=backend.hcl` (see
  # backend.hcl.example) — never hardcoded here, never committed with real
  # values.
  backend "s3" {
    use_lockfile = true
  }
}
