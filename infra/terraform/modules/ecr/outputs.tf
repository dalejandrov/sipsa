output "repository_name" {
  description = "ECR repository name."
  value       = aws_ecr_repository.app.name
}

output "repository_arn" {
  description = "ARN of the ECR repository."
  value       = aws_ecr_repository.app.arn
}

output "repository_url" {
  description = "Repository URL — combine with an immutable image tag (\"<repository_url>:<tag>\") for the ECS task definition's image reference. Never combine with \"latest\" for a real deployment."
  value       = aws_ecr_repository.app.repository_url
}
