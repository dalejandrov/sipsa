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
