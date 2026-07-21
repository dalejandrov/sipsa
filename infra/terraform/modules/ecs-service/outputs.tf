output "alb_arn" {
  description = "ARN of the internal ALB."
  value       = aws_lb.internal.arn
}

output "alb_dns_name" {
  description = "DNS name of the internal ALB. Not a credential, but treated as sensitive by defensive posture — it identifies a reachability point, consistent with modules/database's db_endpoint output."
  value       = aws_lb.internal.dns_name
  sensitive   = true
}

output "alb_zone_id" {
  description = "Route 53 hosted zone ID of the ALB (for a future alias record, if a custom domain is ever added)."
  value       = aws_lb.internal.zone_id
}

output "alb_security_group_id" {
  description = "Security group ID of the ALB — TECH-131 references this to add the VPC Link ingress rule."
  value       = aws_security_group.alb.id
}

output "target_group_arn" {
  description = "ARN of the target group the ECS Service registers into."
  value       = aws_lb_target_group.app.arn
}

output "listener_arn" {
  description = "ARN of the HTTP listener."
  value       = aws_lb_listener.http.arn
}

output "ecs_service_name" {
  description = "ECS Service name."
  value       = aws_ecs_service.app.name
}

output "ecs_service_id" {
  description = "ECS Service ID."
  value       = aws_ecs_service.app.id
}

output "ecs_service_security_group_id" {
  description = "Security group ID of the ECS Service."
  value       = aws_security_group.ecs_service.id
}

output "ecs_desired_count" {
  description = "Configured desired task count. See variables.tf's desired_count description for why this must not be raised above 1 without addressing the scheduler's multi-replica risk first."
  value       = aws_ecs_service.app.desired_count
}
