output "vpc_id" {
  description = "ID of the production VPC."
  value       = aws_vpc.main.id
}

output "vpc_cidr" {
  description = "CIDR block of the production VPC."
  value       = aws_vpc.main.cidr_block
}

output "availability_zones" {
  description = "The two Availability Zones this network was deployed into, selected deterministically at apply time."
  value       = local.azs
}

output "public_subnet_ids" {
  description = "IDs of the two public subnets, in AZ order."
  value       = aws_subnet.public[*].id
}

output "private_app_subnet_ids" {
  description = "IDs of the two private application subnets (for ECS, TECH-132), in AZ order."
  value       = aws_subnet.private_app[*].id
}

output "private_database_subnet_ids" {
  description = "IDs of the two private database subnets (for a future RDS DB subnet group, TECH-132), in AZ order."
  value       = aws_subnet.private_database[*].id
}

output "public_route_table_id" {
  description = "ID of the single, shared public route table."
  value       = aws_route_table.public.id
}

output "private_app_route_table_ids" {
  description = "IDs of the two per-AZ private application route tables, in AZ order."
  value       = aws_route_table.private_app[*].id
}

output "database_route_table_ids" {
  description = "IDs of the two per-AZ private database route tables, in AZ order."
  value       = aws_route_table.database[*].id
}

output "internet_gateway_id" {
  description = "ID of the Internet Gateway attached to the VPC."
  value       = aws_internet_gateway.main.id
}

output "nat_gateway_id" {
  description = "ID of the single NAT Gateway (accepted single point of failure — see README.md)."
  value       = aws_nat_gateway.main.id
}

output "s3_gateway_endpoint_id" {
  description = "ID of the S3 Gateway VPC Endpoint."
  value       = aws_vpc_endpoint.s3.id
}

output "flow_log_group_name" {
  description = "CloudWatch Logs group name for VPC Flow Logs, or null when enable_vpc_flow_logs is false."
  value       = var.enable_vpc_flow_logs ? aws_cloudwatch_log_group.flow_logs[0].name : null
}
