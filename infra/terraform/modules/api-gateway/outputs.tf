output "rest_api_id" {
  description = "REST API ID."
  value       = aws_api_gateway_rest_api.main.id
}

output "rest_api_arn" {
  description = "ARN of the REST API."
  value       = aws_api_gateway_rest_api.main.arn
}

output "execution_arn" {
  description = "Execution ARN of the REST API (base for building Lambda-permission-style or IAM-policy resource ARNs, if ever needed)."
  value       = aws_api_gateway_rest_api.main.execution_arn
}

output "invoke_url" {
  description = "Base invoke URL for the deployed stage (AWS-managed execute-api endpoint — no custom domain in this story). Not sensitive: this is the intended public entry point."
  value       = aws_api_gateway_stage.main.invoke_url
}

output "stage_name" {
  description = "Deployed stage name."
  value       = aws_api_gateway_stage.main.stage_name
}

output "vpc_link_id" {
  description = "ID of the classic REST API VPC Link (targets the NLB fronting the internal ALB)."
  value       = aws_api_gateway_vpc_link.main.id
}

output "usage_plan_id" {
  description = "ID of the general usage plan governing API-key-bearing GET /api/sipsa/** consumers."
  value       = aws_api_gateway_usage_plan.general.id
}

output "api_key_ids" {
  description = "IDs of every API key created by this module. Never the key values themselves — see this module's README.md for how a consumer retrieves their key value (aws apigateway get-api-key --include-value, gated by IAM, never via Terraform output/state exposure)."
  value       = [aws_api_gateway_api_key.consumer.id]
}

output "access_log_group_name" {
  description = "CloudWatch Logs group name for API Gateway access logs."
  value       = aws_cloudwatch_log_group.access_logs.name
}

output "authorizer_id" {
  description = "ID of the Cognito authorizer, in case another stack needs to reference it directly."
  value       = aws_api_gateway_authorizer.cognito.id
}
