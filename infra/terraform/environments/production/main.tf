provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

locals {
  # Every module added to this stack (network, cognito, database, ecs,
  # api-gateway, observability — added one real story at a time, per
  # ADR-010's Fase 1-5 sequence) must apply these tags. Centralized here so
  # a tag change only happens in one place.
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    Owner       = var.owner
    ManagedBy   = var.managed_by
    Repository  = var.repository
    CostCenter  = var.cost_center
  }
}

# No child modules are instantiated yet. TECH-137 (this story) is scaffolding
# only, per ADR-010 Fase 0 — network/compute/identity/gateway modules are
# added in Fase 1-4, each its own reviewable branch, not bundled here.
