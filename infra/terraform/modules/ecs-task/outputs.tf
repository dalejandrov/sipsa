output "ecs_cluster_id" {
  description = "ECS cluster ID."
  value       = aws_ecs_cluster.main.id
}

output "ecs_cluster_arn" {
  description = "ECS cluster ARN."
  value       = aws_ecs_cluster.main.arn
}

output "task_definition_arn" {
  description = "ARN of the latest task definition revision."
  value       = aws_ecs_task_definition.app.arn
}

output "task_definition_family" {
  description = "Task definition family name."
  value       = aws_ecs_task_definition.app.family
}

output "execution_role_arn" {
  description = "ARN of the ECS execution role (used by the ECS agent to prepare the task — image pull, log write, secrets resolution)."
  value       = aws_iam_role.execution.arn
}

output "task_role_arn" {
  description = "ARN of the ECS task role (assumed by the application at runtime — empty permissions by default)."
  value       = aws_iam_role.task.arn
}

output "application_log_group_name" {
  description = "CloudWatch Logs group name for the application container."
  value       = aws_cloudwatch_log_group.app.name
}

output "container_name" {
  description = "Container name within the task definition."
  value       = var.container_name
}

output "container_port" {
  description = "Port the container listens on."
  value       = var.container_port
}
