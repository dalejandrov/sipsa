package com.dalejandrov.sipsa.infrastructure.config.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

/**
 * OAuth 2.0 Resource Server configuration (ADR-002, TECH-001/TECH-002).
 * <p>
 * This is the application layer of a layered security model. In the target AWS
 * deployment, API Gateway terminates the public surface (per-consumer API keys, usage
 * plans, throttling — TECH-131), Cognito issues the JWTs (TECH-130), and the network
 * keeps the backend reachable only through the gateway (TECH-132). This class is the
 * defense-in-depth layer: the backend re-validates every JWT and its scopes itself, so a
 * gateway bypass never grants access to internal operations.
 * <p>
 * <b>Access policy (default deny):</b>
 * <ul>
 *   <li>{@code GET /api/sipsa/**} — public in the application; consumer identification
 *       and metering happen at API Gateway via API keys, which are not authentication.</li>
 *   <li>{@code /api/internal/**} — requires a Cognito access token with the per-operation
 *       scope ({@code sipsa/ingestion.execute}, {@code sipsa/ingestion.cancel},
 *       {@code sipsa/ingestion.read}, {@code sipsa/audit.read}).</li>
 *   <li>{@code /actuator/health} — public for container/platform healthchecks; never
 *       routed through API Gateway.</li>
 *   <li>Rest of {@code /actuator/**} — any valid access token (exposure is already
 *       profile-restricted; this is the extra belt).</li>
 *   <li>Anything else — denied.</li>
 * </ul>
 * <p>
 * Stateless by construction: no sessions, no CSRF surface, no form login, no HTTP Basic,
 * no cookies. Errors are JSON via {@link RestAuthenticationEntryPoint} (401) and
 * {@link RestAccessDeniedHandler} (403).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Boot forwards unresolved/error requests to /error internally;
                        // without this, a plain 404 on a public route would surface as 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sipsa/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/internal/ingestion/run")
                        .hasAuthority("SCOPE_sipsa/ingestion.execute")
                        .requestMatchers(HttpMethod.POST, "/api/internal/ingestion/cancel/*")
                        .hasAuthority("SCOPE_sipsa/ingestion.cancel")
                        .requestMatchers(HttpMethod.GET, "/api/internal/ingestion/**")
                        .hasAuthority("SCOPE_sipsa/ingestion.read")
                        .requestMatchers(HttpMethod.GET, "/api/internal/audit/**")
                        .hasAuthority("SCOPE_sipsa/audit.read")
                        .requestMatchers("/actuator/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * JWT decoder for the configured issuer with the Cognito-specific validators.
     * <p>
     * Wrapped in a {@link SupplierJwtDecoder} so the OIDC metadata/JWKS fetch happens on
     * the first token validation, not at startup — the application (and the test suite)
     * must be able to boot without network access to the issuer. Configuration problems
     * (missing issuer, malformed allowlist) still fail fast at startup via
     * {@link SipsaJwtProperties}.
     */
    @Bean
    public JwtDecoder jwtDecoder(SipsaJwtProperties properties) {
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(properties.issuerUri()).build();

            List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
            validators.add(JwtValidators.createDefaultWithIssuer(properties.issuerUri()));
            validators.add(new TokenUseValidator());
            if (!properties.allowedClientIds().isEmpty()) {
                validators.add(new AllowedClientIdsValidator(properties.allowedClientIds()));
            }
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
            return decoder;
        });
    }
}
