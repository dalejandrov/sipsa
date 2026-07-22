output "user_pool_id" {
  description = "Cognito user pool ID."
  value       = aws_cognito_user_pool.main.id
}

output "user_pool_arn" {
  description = "ARN of the Cognito user pool."
  value       = aws_cognito_user_pool.main.arn
}

output "issuer_url" {
  description = <<-EOT
    OIDC issuer URL for this user pool — wire this into SIPSA_JWT_ISSUER_URI
    for a real deployment. Built from the pool's own "endpoint" attribute
    (format: cognito-idp.<region>.amazonaws.com/<pool-id>), prefixed with
    https:// to match the URL format SipsaJwtProperties/Spring's OAuth2
    Resource Server auto-configuration expects.
  EOT
  value       = "https://${aws_cognito_user_pool.main.endpoint}"
}

output "resource_server_identifier" {
  description = "Resource server identifier (\"sipsa\" by default) — the scope prefix SecurityConfig already enforces."
  value       = aws_cognito_resource_server.sipsa.identifier
}

output "m2m_client_id" {
  description = "Client ID of the M2M (client_credentials) app client. An identifier, not a secret."
  value       = aws_cognito_user_pool_client.m2m.id
}

output "human_client_id" {
  description = "Client ID of the human (Authorization Code + PKCE) app client. An identifier, not a secret; this client has no secret at all (public client)."
  value       = aws_cognito_user_pool_client.human.id
}

output "cognito_domain" {
  description = "Cognito Hosted UI domain, or null when create_hosted_ui_domain is false (the default — see variables.tf)."
  value       = var.create_hosted_ui_domain ? aws_cognito_user_pool_domain.hosted_ui[0].domain : null
}

output "m2m_client_secret_arn" {
  description = "ARN of the Secrets Manager secret holding the M2M client's credentials. Never the secret value itself — see modules/cognito/README.md for the retrieval/distribution strategy."
  value       = aws_secretsmanager_secret.m2m_client_secret.arn
}

output "allowed_client_ids_parameter_name" {
  description = "SSM Parameter Store parameter name publishing the CSV of app client IDs, or null when publish_client_ids_to_ssm is false. Not the value itself (though the value is non-sensitive — see variables.tf)."
  value       = var.publish_client_ids_to_ssm ? aws_ssm_parameter.allowed_client_ids[0].name : null
}
