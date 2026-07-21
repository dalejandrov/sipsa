# Re-exposes the network module's outputs (TECH-138) at the root, so later
# stories (TECH-132's compute/database phases, TECH-130, TECH-131) can
# reference this stack's outputs without reaching into module internals.
# No sensitive value is exposed — every output here is a resource ID/ARN or
# a non-secret name.

output "vpc_id" {
  description = "ID of the production VPC."
  value       = module.network.vpc_id
}

output "vpc_cidr" {
  description = "CIDR block of the production VPC."
  value       = module.network.vpc_cidr
}

output "availability_zones" {
  description = "The two Availability Zones the network was deployed into."
  value       = module.network.availability_zones
}

output "public_subnet_ids" {
  description = "IDs of the two public subnets."
  value       = module.network.public_subnet_ids
}

output "private_app_subnet_ids" {
  description = "IDs of the two private application subnets (for ECS, TECH-132)."
  value       = module.network.private_app_subnet_ids
}

output "private_database_subnet_ids" {
  description = "IDs of the two private database subnets (for a future RDS DB subnet group, TECH-132)."
  value       = module.network.private_database_subnet_ids
}

output "public_route_table_id" {
  description = "ID of the shared public route table."
  value       = module.network.public_route_table_id
}

output "private_app_route_table_ids" {
  description = "IDs of the two per-AZ private application route tables."
  value       = module.network.private_app_route_table_ids
}

output "database_route_table_ids" {
  description = "IDs of the two per-AZ private database route tables."
  value       = module.network.database_route_table_ids
}

output "internet_gateway_id" {
  description = "ID of the Internet Gateway."
  value       = module.network.internet_gateway_id
}

output "nat_gateway_id" {
  description = "ID of the single NAT Gateway."
  value       = module.network.nat_gateway_id
}

output "s3_gateway_endpoint_id" {
  description = "ID of the S3 Gateway VPC Endpoint."
  value       = module.network.s3_gateway_endpoint_id
}

output "flow_log_group_name" {
  description = "CloudWatch Logs group name for VPC Flow Logs, or null when disabled."
  value       = module.network.flow_log_group_name
}

# --- Database (TECH-139) ---------------------------------------------------

output "db_instance_id" {
  description = "RDS instance identifier."
  value       = module.database.db_instance_id
}

output "db_instance_arn" {
  description = "ARN of the RDS instance."
  value       = module.database.db_instance_arn
}

output "db_endpoint" {
  description = "RDS connection endpoint (host:port). Sensitive by defensive posture — see modules/database/README.md."
  value       = module.database.db_endpoint
  sensitive   = true
}

output "db_port" {
  description = "PostgreSQL port."
  value       = module.database.db_port
}

output "db_name" {
  description = "Initial database name."
  value       = module.database.db_name
}

output "db_security_group_id" {
  description = "Security group ID for the RDS instance — no ingress rule exists yet (TECH-139); TECH-132's compute phase adds one via the database module's allowed_security_group_ids."
  value       = module.database.db_security_group_id
}

output "db_subnet_group_name" {
  description = "Name of the DB subnet group."
  value       = module.database.db_subnet_group_name
}

output "master_secret_arn" {
  description = "ARN of the Secrets Manager secret RDS manages for the master password — never the secret's value (see modules/database/README.md)."
  value       = module.database.master_secret_arn
}

# --- Container registry (TECH-140) ------------------------------------------

output "ecr_repository_name" {
  description = "ECR repository name."
  value       = module.ecr.repository_name
}

output "ecr_repository_arn" {
  description = "ARN of the ECR repository."
  value       = module.ecr.repository_arn
}

output "ecr_repository_url" {
  description = "ECR repository URL — combine with an immutable image tag for the task definition's image reference."
  value       = module.ecr.repository_url
}

# --- ECS cluster and task definition (TECH-140) -----------------------------

output "ecs_cluster_id" {
  description = "ECS cluster ID."
  value       = module.ecs_task.ecs_cluster_id
}

output "ecs_cluster_arn" {
  description = "ECS cluster ARN."
  value       = module.ecs_task.ecs_cluster_arn
}

output "task_definition_arn" {
  description = "ARN of the latest task definition revision."
  value       = module.ecs_task.task_definition_arn
}

output "task_definition_family" {
  description = "Task definition family name."
  value       = module.ecs_task.task_definition_family
}

output "execution_role_arn" {
  description = "ARN of the ECS execution role."
  value       = module.ecs_task.execution_role_arn
}

output "task_role_arn" {
  description = "ARN of the ECS task role."
  value       = module.ecs_task.task_role_arn
}

output "application_log_group_name" {
  description = "CloudWatch Logs group name for the application container."
  value       = module.ecs_task.application_log_group_name
}

output "container_name" {
  description = "Container name within the task definition."
  value       = module.ecs_task.container_name
}

output "container_port" {
  description = "Port the container listens on."
  value       = module.ecs_task.container_port
}

# --- Internal ALB and ECS Service (TECH-141) --------------------------------

output "alb_arn" {
  description = "ARN of the internal ALB."
  value       = module.ecs_service.alb_arn
}

output "alb_dns_name" {
  description = "DNS name of the internal ALB. Sensitive by defensive posture (see modules/ecs-service/README.md)."
  value       = module.ecs_service.alb_dns_name
  sensitive   = true
}

output "alb_zone_id" {
  description = "Route 53 hosted zone ID of the ALB."
  value       = module.ecs_service.alb_zone_id
}

output "alb_security_group_id" {
  description = "Security group ID of the ALB — TECH-131 references this to add the VPC Link ingress rule."
  value       = module.ecs_service.alb_security_group_id
}

output "target_group_arn" {
  description = "ARN of the target group the ECS Service registers into."
  value       = module.ecs_service.target_group_arn
}

output "listener_arn" {
  description = "ARN of the HTTP listener."
  value       = module.ecs_service.listener_arn
}

output "ecs_service_name" {
  description = "ECS Service name."
  value       = module.ecs_service.ecs_service_name
}

output "ecs_service_id" {
  description = "ECS Service ID."
  value       = module.ecs_service.ecs_service_id
}

output "ecs_service_security_group_id" {
  description = "Security group ID of the ECS Service."
  value       = module.ecs_service.ecs_service_security_group_id
}

output "ecs_desired_count" {
  description = "Configured desired task count. Must not be raised above 1 without addressing the scheduler's multi-replica risk first (see modules/ecs-service/README.md)."
  value       = module.ecs_service.ecs_desired_count
}
