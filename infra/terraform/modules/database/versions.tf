terraform {
  required_version = ">= 1.14.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 6.0.0, < 7.0.0"
    }
    # Used exclusively to generate a stable, unique suffix for the RDS final
    # snapshot identifier (see main.tf) — final snapshot names must be
    # unique per AWS account/region, and a static name would fail on a
    # second destroy/recreate cycle that reuses it.
    random = {
      source  = "hashicorp/random"
      version = ">= 3.6.0, < 4.0.0"
    }
  }
}
