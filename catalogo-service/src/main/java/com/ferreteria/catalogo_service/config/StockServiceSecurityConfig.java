package com.ferreteria.catalogo_service.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class StockServiceSecurityConfig {
    public static final String ISSUER = "checkpoint-stock-services";
    public static final String AUDIENCE = "catalogo-stock";
    public static final String SUBJECT = "venta-service";
    public static final String AUTHORITY = "SCOPE_stock:operar";

    @Bean
    @Order(1)
    public SecurityFilterChain stockServiceSecurityFilterChain(HttpSecurity http,
            @Value("${jwt.secret:${JWT_SECRET}}") String humanSecret) throws Exception {
        // Exclusivamente entorno: no placeholder con fallback a properties.
        String secret = System.getenv("STOCK_SERVICE_JWT_SECRET");
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.equals(humanSecret)) {
            throw new IllegalArgumentException(
                    "STOCK_SERVICE_JWT_SECRET requiere al menos 32 bytes y debe ser independiente de JWT_SECRET");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(ISSUER),
                this::validarIdentidad));
        // Decoder local a esta cadena: no agrega un segundo bean JwtDecoder ni altera el humano.
        return http.securityMatcher("/internal", "/internal/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST,
                                "/internal/stock/operaciones/{id}/aplicar",
                                "/internal/stock/operaciones/{id}/revertir").hasAuthority(AUTHORITY)
                        .requestMatchers(HttpMethod.GET,
                                "/internal/stock/operaciones/{id}").hasAuthority(AUTHORITY)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> jwt.decoder(decoder))
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setHeader("WWW-Authenticate", "Bearer");
                            response.setContentType("application/json");
                            response.getWriter().write("{\"codigo\":\"SERVICIO_NO_AUTENTICADO\"}");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"codigo\":\"SERVICIO_SIN_PERMISO\"}");
                        }))
                .build();
    }

    private OAuth2TokenValidatorResult validarIdentidad(Jwt jwt) {
        Instant issued = jwt.getIssuedAt();
        Instant expires = jwt.getExpiresAt();
        if (SUBJECT.equals(jwt.getSubject()) && jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                && !jwt.hasClaim("role") && jwt.getClaims().get("scope") instanceof String
                && issued != null && expires != null && !issued.isAfter(Instant.now())
                && expires.isAfter(issued) && !expires.isAfter(issued.plusSeconds(300))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Identidad de servicio inválida", null));
    }
}
