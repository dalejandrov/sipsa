# network module

Production VPC foundation for SIPSA (TECH-138), per
[ADR-010](../../../../docs/adr/ADR-010-aws-infrastructure-as-code.md). Creates the VPC,
subnets, routing, NAT, S3 Gateway Endpoint, and VPC Flow Logs — **no compute (ECS/ALB),
no database (RDS), no security groups for those resources**. Those are added by their own
stories (TECH-132's later phases) against the outputs this module exposes.

## Topology

```
VPC 10.40.0.0/16 (2 AZs, selected deterministically via data.aws_availability_zones)

Public:                    Private application:        Private database:
  AZ A  10.40.0.0/24         AZ A  10.40.10.0/24          AZ A  10.40.20.0/24
  AZ B  10.40.1.0/24         AZ B  10.40.11.0/24          AZ B  10.40.21.0/24
```

All three CIDR ranges (and the VPC CIDR itself) are Terraform variables with `cidrhost`
validation — never hardcoded literals inside a resource block.

## Routing

| Tier | Route table | Default route |
|---|---|---|
| Public | One shared table, both public subnets | `0.0.0.0/0` → Internet Gateway |
| Private application | **One table per AZ** | `0.0.0.0/0` → the single NAT Gateway (both AZs' tables point at the same NAT today) |
| Private database | **One table per AZ** | none — no route to the NAT Gateway or the Internet Gateway exists |

**Why per-AZ tables for private-app and database, but one shared table for public:** the
two public subnets have no reason to ever route differently from each other. The private
application subnets might — specifically, the documented future NAT-per-AZ migration
(below) re-points one AZ's route table to its own NAT Gateway without touching the other
AZ's table, which a shared table couldn't do without splitting it first. Database subnets
are kept per-AZ for the same structural symmetry, even though neither declares an
internet-facing route today.

**Database subnets are deliberately unreachable from the internet and from the NAT
Gateway** — reserved for a future RDS DB subnet group (TECH-132). No `aws_route` sends
their traffic anywhere except the VPC's implicit local route.

## NAT Gateway: one, not one per AZ

A single NAT Gateway is created, in the first selected AZ's public subnet. Both private
application subnets' route tables send `0.0.0.0/0` through it.

- **Cost:** roughly half of a two-NAT setup (one hourly charge instead of two).
- **Accepted risk — single point of failure:** if this NAT Gateway's AZ has an outage,
  **every** private application subnet loses internet egress, including the subnet in the
  *other*, otherwise-healthy AZ. There is no redundant egress path in this topology.
- **Accepted risk — cross-AZ data transfer:** traffic originating in the second AZ's
  private application subnet crosses an Availability Zone boundary to reach the NAT
  Gateway in the first AZ, incurring the standard AWS cross-AZ data-transfer charge that a
  same-AZ NAT path would not.
- **Future migration:** add a second NAT Gateway (with its own EIP) in the second public
  subnet, then update `aws_route_table.private_app[1]`'s route to point at it instead of
  the shared NAT. This is a route-table change plus one additional NAT Gateway/EIP pair —
  not a VPC or subnet redesign — because the subnet and route-table layout is already
  per-AZ from the start.

No NAT Instance is used (a managed NAT Gateway, not a self-managed EC2-based NAT, per
ADR-010).

## S3 Gateway VPC Endpoint

Associated with the two private-app route tables only (not public, not database — no
current workload in those tiers needs it). A Gateway endpoint has **no hourly or
per-GB-processed charge** (unlike an Interface endpoint), so this is added outright rather
than deferred:

- Keeps S3-bound traffic off the NAT Gateway entirely (reduces NAT data-processing cost
  and cross-AZ transfer for that traffic specifically).
- Will benefit ECR image pulls once TECH-132 introduces ECS — ECR stores image layers in
  S3, and pulls would otherwise traverse the NAT Gateway.

**Interface endpoints are deliberately not added** for ECR API, ECR DKR, CloudWatch Logs,
Secrets Manager, or STS — each carries an hourly charge plus per-GB processing, unlike the
S3 Gateway endpoint. Reconsider once real NAT Gateway traffic volume justifies the
trade-off, or a specific security requirement (e.g., no traffic to these services may ever
traverse the public internet path, even via NAT) makes one necessary regardless of cost.

## VPC Flow Logs

Configurable via `enable_vpc_flow_logs` (default `true` for production). When enabled:

- **`traffic_type = "REJECT"` by default** (`flow_log_traffic_type` variable) — rejected
  traffic is the most useful signal for diagnosing security-group/NACL misconfiguration;
  capturing only `REJECT` keeps log volume, and therefore cost, roughly proportional to
  actual connectivity problems rather than every accepted packet (`ALL` would capture
  many orders of magnitude more data for comparatively little additional diagnostic
  value at this stage).
- **30-day CloudWatch Logs retention by default** (`flow_log_retention_days`) — never
  indefinite; the variable has no zero/infinite default and is not optional to bound.
- A dedicated IAM role and inline policy exist solely for this purpose — the policy is
  scoped to the three log actions VPC Flow Logs needs
  (`CreateLogStream`/`PutLogEvents`/`Describe*`) against this module's own log group ARN,
  not a wildcard.

Disabling Flow Logs is possible (`enable_vpc_flow_logs = false`) but must be a deliberate,
explicit variable override — never a default for a production environment.

## What this module does NOT create

Per TECH-138's explicit scope: no ECS, ALB, RDS, Cognito, API Gateway, VPC Link, Secrets
Manager, ECR, WAF, Route 53, or ACM resource. No security group is created here either —
ALB/ECS/RDS security groups are created alongside the resources that actually consume
their rules, in the stories that add those resources, not speculatively here.

## Testing

`tests/network.tftest.hcl` uses Terraform's native `terraform test` with a mocked AWS
provider (`mock_provider "aws" {}`) — no real AWS account or credential is contacted. Run
with:

```bash
terraform test
```

from this module's directory (after `terraform init -backend=false`).
