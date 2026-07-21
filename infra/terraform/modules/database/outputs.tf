output "db_instance_id" {
  description = "RDS instance identifier."
  value       = aws_db_instance.main.id
}

output "db_instance_arn" {
  description = "ARN of the RDS instance."
  value       = aws_db_instance.main.arn
}

output "db_endpoint" {
  description = "Connection endpoint (host:port). Not a credential, but treated as sensitive by defensive posture — it identifies exactly where the database is reachable from, which is more useful to an attacker than to leave casually visible in plan/apply output or a state-viewing tool's default display."
  value       = aws_db_instance.main.endpoint
  sensitive   = true
}

output "db_port" {
  description = "PostgreSQL port."
  value       = aws_db_instance.main.port
}

output "db_address" {
  description = "Database hostname only (no port) — for wiring into a consumer's DB_HOST-style environment variable (TECH-140's ecs-task module). Not marked sensitive: a bare hostname, without the port or any credential, is treated the same as this module's other infrastructure-identifier outputs (e.g. db_subnet_group_name)."
  value       = aws_db_instance.main.address
}

output "db_name" {
  description = "Initial database name."
  value       = aws_db_instance.main.db_name
}

output "db_security_group_id" {
  description = "Security group ID for the RDS instance — pass this to a future consumer's own security group rule, or add this module's allowed_security_group_ids with the consumer's SG id, to grant access. No ingress rule exists until one of those is done."
  value       = aws_security_group.rds.id
}

output "db_subnet_group_name" {
  description = "Name of the DB subnet group."
  value       = aws_db_subnet_group.main.name
}

output "master_secret_arn" {
  description = <<-EOT
    ARN of the Secrets Manager secret RDS created and manages for the master
    user password (manage_master_user_password = true) — never the secret's
    value itself, which Terraform never sees or stores. Not sensitive: an
    ARN is a resource identifier, not a credential; reading the actual
    secret value requires a separate IAM permission
    (secretsmanager:GetSecretValue) this ARN alone does not grant. Whichever
    IAM role/user needs runtime DB access (the future ECS task role, per
    TECH-132) is the intended future reader of this secret — grant that
    permission explicitly when that role exists, not implicitly via this
    output.
  EOT
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
}
