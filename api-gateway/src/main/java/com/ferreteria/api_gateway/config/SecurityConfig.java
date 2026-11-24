package com.ferreteria.api_gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

        @Value("${spring.security.oauth2.resourceserver.jwt.secret-key}")
        private String secret;

        @Bean
        public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
                return http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(csrf -> csrf.disable())
                                .authorizeExchange(exchanges -> exchanges
                                                .pathMatchers("/internal", "/internal/**").denyAll()
                                                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                                                // RUTAS PÚBLICAS
                                                .pathMatchers(HttpMethod.POST, "/auth/login", "/auth/register/cliente")
                                                .permitAll()
                                                .pathMatchers(HttpMethod.GET, "/auth/v3/api-docs", "/auth/v3/api-docs/**")
                                                .permitAll()
                                                .pathMatchers("/auth/**").denyAll()
                                                // Pedidos nuevos: el literal debe ir antes del patrón {id}.
                                                .pathMatchers(HttpMethod.POST, "/api/pedidos").hasRole("CLIENTE")
                                                .pathMatchers(HttpMethod.GET, "/api/pedidos/mis-pedidos")
                                                .hasRole("CLIENTE")
                                                .pathMatchers(HttpMethod.GET, "/api/pedidos/{id}")
                                                .hasAnyRole("CLIENTE", "ADMINISTRADOR")
                                                .pathMatchers(HttpMethod.GET,
                                                                "/api/admin/pedidos", "/api/admin/pedidos/{id}")
                                                .hasRole("ADMINISTRADOR")
                                                // Antes de Swagger genérico: sin rutas ni escrituras heredadas.
                                                .pathMatchers("/api/pedidos", "/api/pedidos/**",
                                                                "/api/admin/pedidos", "/api/admin/pedidos/**").denyAll()
                                                // Ventas: lista cerrada antes de las excepciones genéricas de Swagger.
                                                .pathMatchers(HttpMethod.GET,
                                                                "/api/ventas/v3/api-docs", "/api/ventas/v3/api-docs/**")
                                                .permitAll()
                                                .pathMatchers(HttpMethod.GET,
                                                                "/api/ventas", "/api/ventas/{id}",
                                                                "/api/ventas/usuario/{usuarioId}", "/api/ventas/rango-fechas")
                                                .hasRole("ADMINISTRADOR")
                                                // Bloquea escrituras y consulta por email incluso para ADMINISTRADOR.
                                                .pathMatchers("/api/ventas", "/api/ventas/**").denyAll()
                                                .pathMatchers(
                                                                "/doc/**",
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/webjars/**",
                                                                "/swagger-resources/**",
                                                                "/api/*/v3/api-docs",
                                                                "/api/*/v3/api-docs/**")
                                                .permitAll()

                                                // PRODUCTOS (CATÁLOGO)
                                                .pathMatchers(HttpMethod.GET,
                                                                "/api/admin/productos", "/api/admin/productos/{id}")
                                                .hasRole("ADMINISTRADOR")
                                                .pathMatchers(HttpMethod.POST, "/api/productos/rawg/{rawgId}/importar")
                                                .hasRole("ADMINISTRADOR")
                                                .pathMatchers(HttpMethod.POST, "/api/productos/imagenes/subir")
                                                .hasRole("ADMINISTRADOR")
                                                .pathMatchers(HttpMethod.GET, "/api/productos/imagenes/{nombre}")
                                                .permitAll()
                                                .pathMatchers(HttpMethod.GET,
                                                                "/api/productos/rawg/buscar",
                                                                "/api/productos/rawg/{rawgId}")
                                                .hasRole("ADMINISTRADOR")
                                                .pathMatchers(HttpMethod.GET, "/api/productos", "/api/productos/*")
                                                .permitAll()
                                                .pathMatchers(HttpMethod.POST, "/api/productos").hasRole("ADMINISTRADOR")
                                                .pathMatchers(HttpMethod.POST,
                                                                "/api/productos/*/stock/entradas",
                                                                "/api/productos/*/stock/salidas")
                                                .hasRole("ADMINISTRADOR")
                                                .pathMatchers(HttpMethod.PUT, "/api/productos/*").hasRole("ADMINISTRADOR")
                                                .pathMatchers(HttpMethod.DELETE, "/api/productos/*").hasRole("ADMINISTRADOR")

                                                // USUARIOS
                                                .pathMatchers(HttpMethod.GET, "/api/v1/usuarios/me").authenticated()
                                                .pathMatchers(HttpMethod.PATCH, "/api/v1/usuarios/me").authenticated()
                                                .pathMatchers(HttpMethod.POST, "/api/v1/admin/usuarios")
                                                .hasRole("ADMINISTRADOR")

                                                // Incluye rutas de los módulos retirados y operaciones no definidas.
                                                .anyExchange().denyAll())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                // Configurar JWT como método de autenticación
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter())))
                                .build();
        }

        // Una sola política, aplicada por Spring Security antes de autenticar.
        // No registrar además CorsWebFilter ni globalcors en application.yml.
        private UrlBasedCorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration cors = new CorsConfiguration();
                cors.setAllowedOrigins(List.of(
                                "http://localhost:5173",
                                "http://127.0.0.1:5173"));
                cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
                cors.setAllowCredentials(false);
                cors.setMaxAge(600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/internal", new CorsConfiguration());
                source.registerCorsConfiguration("/internal/**", new CorsConfiguration());
                source.registerCorsConfiguration("/**", cors);
                return source;
        }

        // Bean para leer la firma del token
        @Bean
        public ReactiveJwtDecoder jwtDecoder() {
                byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
                if (keyBytes.length < 32) {
                        throw new IllegalArgumentException(
                                        "La clave JWT debe contener al menos 32 bytes UTF-8");
                }
                SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");
                NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                                .macAlgorithm(MacAlgorithm.HS256)
                                .build();
                Set<String> roles = Set.of("ADMINISTRADOR", "VENDEDOR", "CLIENTE");
                OAuth2TokenValidator<Jwt> roleValidator = jwt ->
                                jwt.getClaims().get("role") instanceof String role && roles.contains(role)
                                                ? OAuth2TokenValidatorResult.success()
                                                : OAuth2TokenValidatorResult.failure(
                                                                new OAuth2Error("invalid_token", "Rol no válido", null));
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                                JwtValidators.createDefault(), roleValidator));
                return decoder;
        }

        // Leer roles
        @Bean
        public org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
                // Extractor de roles estándar
                org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter authoritiesConverter = new org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter();

                authoritiesConverter.setAuthoritiesClaimName("role");
                authoritiesConverter.setAuthorityPrefix("ROLE_");

                // Conversor reactivo usando nuestro extractor
                org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter jwtConverter = new org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter();

                jwtConverter.setJwtGrantedAuthoritiesConverter(
                                new org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter(
                                                authoritiesConverter));

                return jwtConverter;
        }
}
