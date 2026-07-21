# database module

Production RDS PostgreSQL foundation for SIPSA (TECH-139), per
[ADR-010](../../../../docs/adr/ADR-010-aws-infrastructure-as-code.md). Creates the DB
subnet group, security group (no ingress rule yet), parameter group, and the RDS instance
itself — **no ECS, ALB, ECR, Cognito, API Gateway, VPC Link, Route 53, ACM, WAF, or
application deployment**. No real connection to this database is ever made by this story;
no Flyway migration runs against it.

## Single-AZ: a cost decision, not an oversight

`multi_az = false` by default (ADR-010). Documented trade-off:

- **Why:** Multi-AZ roughly doubles the RDS instance cost (a synchronously-replicated
  standby in a second AZ) for automatic failover this stage's traffic and criticality do
  not yet justify — there is no production user traffic at all yet.
- **Accepted risk:** no automatic failover. An AZ-level outage, or even a routine
  maintenance event (e.g. a forced minor-version patch) affecting the single instance,
  causes a full database outage until AWS completes recovery — which for Single-AZ RDS is
  typically several minutes to longer, not the near-instant failover Multi-AZ provides.
- **Future path:** set `multi_az = true`. This is a one-line variable change, not a
  redesign — the module already exposes it as a variable specifically so this upgrade
  never requires touching the rest of the configuration. **Do this before any real user
  traffic depends on this database**, or once a criticality decision (e.g. an SLA
  commitment) is made — re-evaluate at that point, don't wait for an incident to force
  the decision.

## PostgreSQL version

`postgres_engine_version` defaults to `"18"` (major version only). This was chosen by
inspecting the repository, not assumed:

- `docker-compose.yml`: `postgres:18.0-alpine3.22`
- Every Testcontainers-based integration test (`FlywayMigrationsTest`,
  `ParcialConcurrentIngestionAppTest`, and 8 others): `new PostgreSQLContainer("postgres:18.0-alpine3.22")`
- Flyway: `flyway-database-postgresql`, version-managed by the Spring Boot 4.1.0 parent —
  current Flyway releases support PostgreSQL 18.
- Extensions: only `citext` (`V1__initial_schema.sql`), a standard PostgreSQL contrib
  extension available on RDS for every currently-supported engine version — no extension
  compatibility blocker exists.

**Not verified by this story:** whether RDS actually offers engine version `18` for the
account/region this eventually deploys to — no AWS API call is made by TECH-139 (by
design). Confirm via `aws rds describe-db-engine-versions --engine postgres --query
"DBEngineVersions[].EngineVersion"` (or the AWS console) **before the first real
`terraform apply`**. Using the major-version-only form (`"18"` rather than a specific
`"18.x"`) lets AWS resolve the latest available minor automatically, which is the
AWS-recommended pattern precisely because it avoids pinning to a minor version that may
not exist in a given region.

**Minor-version upgrade policy:** `auto_minor_version_upgrade = true` by default — minor
PostgreSQL releases are backward-compatible patches (security and bug fixes); this
repository has no evidence of a reason to pin a specific minor version. Upgrades apply
only during the `maintenance_window` (see below).

## Instance class and storage — proposals, not confirmed availability

`instance_class` defaults to `"db.t3.micro"` — burstable, **Intel/AMD (not Graviton)**.
Graviton classes (`db.t4g.*`, `db.r6g.*`, etc.) are deliberately not defaulted to: their
availability and compatibility with the chosen `postgres_engine_version` have not been
confirmed against a live AWS account. `db.t3.micro` is proposed as a low-cost, broadly
available starting point — **this is a proposal requiring validation (regional
availability, engine-version compatibility) before the first real apply**, not a
confirmed fact, consistent with how TECH-138 already flagged the same gap for ECS Fargate
task sizing.

Storage: `gp3` (general-purpose SSD, no Provisioned IOPS at this stage — `io1`/`io2` are
not offered by this module's `storage_type` validation). `allocated_storage` defaults to
20 GiB (the RDS PostgreSQL gp3 minimum); `max_allocated_storage` defaults to 100 GiB as an
autoscaling ceiling — **raising this ceiling raises the worst-case cost, not the typical
one**, since RDS storage autoscaling only grows storage (and its cost) as actual usage
approaches the current allocation; growth is never unbounded.

## Network

The DB subnet group (`aws_db_subnet_group.main`) uses **exclusively**
`var.private_database_subnet_ids` — never public or private-application subnets.
`publicly_accessible = false` is hardcoded, not a variable — there is no path to making
this database publicly reachable through this module.

The RDS security group (`aws_security_group.rds`) is created with **no ingress rule** by
this story — no ECS (or any other) consumer security group exists yet to reference. A
future story (TECH-132's compute phase) passes its ECS service's security group ID via
`allowed_security_group_ids`, which creates exactly one `aws_security_group_rule` per ID,
scoped to `source_security_group_id` (never a CIDR block, never `0.0.0.0/0`). Until then,
`db_security_group_id` is exposed as an output specifically so that future story can
reference it without this module needing to change.

## Credentials — RDS-managed, never a Terraform variable

`manage_master_user_password = true`: RDS creates and owns the master password directly
in AWS Secrets Manager. **Terraform never sees, generates, stores, or versions the
password value** — the `aws_db_instance` resource only ever exposes
`master_user_secret[0].secret_arn`, a reference, never the secret's contents. This is why
`master_username` (an identifier, not a secret) is the only credential-adjacent Terraform
variable this module defines — there is deliberately no `password` variable design here.

`master_secret_arn` (module output) is **not marked sensitive** — an ARN is a resource
identifier, not a credential; reading the actual secret value requires the separate IAM
permission `secretsmanager:GetSecretValue`, which this ARN alone does not grant. The
intended future reader of that secret is the ECS task role TECH-132 will create — grant
`secretsmanager:GetSecretValue` on this specific secret ARN to that role explicitly, when
it exists. This story does not grant that permission to anything, since nothing exists
yet to grant it to. **Custom secret rotation is explicitly not implemented in this
story** — RDS-managed master-password rotation is a separate, later decision.

## Backup and maintenance windows — explicit UTC conversion

The application's ingestion scheduler (`application.yaml`) runs on `America/Bogota`
(UTC-5, no DST): the daily ingestion window is `14:20`-`23:59` Bogota, and monthly jobs
(days 8 and 10) fire at `14:30` Bogota within that same window. Converted to UTC (Bogota
+ 5 hours):

```
Daily/monthly ingestion window (UTC): 19:20 (day N) -- 04:59 (day N+1)
Remaining safe window (UTC):          05:00 -- 19:19, every day of the month
```

- **`backup_window` default `"06:00-06:30"` UTC** = `01:00`-`01:30` AM Bogota — inside the
  safe window with wide margin on every day, including days 8 and 10.
- **`maintenance_window` default `"sun:07:00-sun:08:00"` UTC** = Sunday `02:00`-`03:00` AM
  Bogota — also inside the safe window, and deliberately not overlapping the backup
  window (AWS best practice: keep the two apart).

No local time was used without this explicit conversion.

## Final snapshot: reproducible, not static

`skip_final_snapshot = false` by default — a final snapshot is taken on deletion.
AWS requires snapshot identifiers to be unique per account/region; a static name (e.g.
`"sipsa-production-postgres-final"`) would fail on a **second** destroy that reuses it,
since the first destroy's snapshot would still exist under that name. This module uses
`random_id.final_snapshot_suffix` (from the `hashicorp/random` provider) to generate a
stable-per-instance-lifetime, always-unique-across-recreations suffix — stable across
repeated `plan`/`apply` of the *same* instance (no spurious diff), but fresh whenever the
instance itself is replaced.

## Parameter group

Created only because there are real parameters to manage — not created empty for its own
sake. Currently sets exactly two: `log_connections = 1` and `log_disconnections = 1`
(low-volume, operationally useful audit signal). **`rds.force_ssl` is deliberately NOT
set**: this application's JDBC URL (`application.yaml`) does not specify an `sslmode`
today, and forcing SSL at the database before confirming the JDBC client negotiates it
correctly would risk breaking the production connection outright the first time it's
attempted. This is a documented gap, not a silent decision either way — resolve it (both
the JDBC-side `sslmode` and this parameter) together, in a follow-up story, before real
traffic depends on this database. PostgreSQL's `timezone` parameter is left at its RDS
default (`UTC`) — this repository's own timezone handling already happens entirely at the
application layer (`America/Bogota`, per `application.yaml`), and RDS's default is already
UTC, so there is no evidence to override it.

## Monitoring — deliberately minimal at this stage

- **Performance Insights: disabled** (`performance_insights_enabled = false`) — an added
  cost with no established need yet.
- **Enhanced Monitoring: disabled** (`monitoring_interval = 0`) — same reasoning; standard
  CloudWatch metrics (CPU, storage, connections, IOPS) remain available regardless of this
  setting.
- **What's lost by leaving both disabled:** Performance Insights' per-query performance
  visibility, and Enhanced Monitoring's OS-level (not just RDS-API-level) metrics at
  sub-minute granularity. Neither is needed to detect "the database is down" or "storage
  is running low" — both remain visible via standard CloudWatch metrics — but query-level
  performance debugging will be harder without Performance Insights. **Enable both once
  real production traffic exists and a performance question actually needs answering** —
  turning them on later is a variable flip, not a redesign.
- **CloudWatch Logs exports:** `postgresql` and `upgrade` only (low-volume). Query-level
  or audit logging (`pgaudit`, `log_statement=all`) is not enabled — its volume against
  this application's real traffic has never been measured, and defaulting it on risks
  unbudgeted log-ingestion cost. Retention: 30 days by default (`log_retention_days`),
  never infinite — log groups are created by this module ahead of the RDS instance
  specifically so retention applies from the start (RDS's own auto-created log groups
  default to never expire otherwise).

## Trivy exceptions

See the inline `# trivy:ignore:<AVD-ID>` comments in `main.tf` for each exception, with
its resource, risk, and justification — reassessed for this module specifically, never
copied mechanically from another module's exceptions.

## Testing

`tests/database.tftest.hcl` uses Terraform's native `terraform test` with a mocked AWS
provider (`mock_provider "aws" {}`) — no real AWS account or credential is contacted. Run
with `terraform test` from this module's directory (after `terraform init -backend=false`).
