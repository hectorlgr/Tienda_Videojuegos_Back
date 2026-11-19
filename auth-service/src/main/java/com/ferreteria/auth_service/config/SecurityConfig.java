package com.ferreteria.auth_service.config;

import java.math.BigDecimal;

import javax.crypto.SecretKey;

import io.jsonwebtoken.security.Keys;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(authorities);

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(requests -> requests
                        // Permite el despacho interno de errores, no una ruta pública /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register/cliente").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/auth/v3/api-docs", "/auth/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/v1/usuarios/me").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authentication)))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        // Misma conversión y selección de clave que el emisor actual JwtService.
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        if (!"HmacSHA256".equals(key.getAlgorithm())) {
            throw new IllegalArgumentException(
                    "jwt.secret debe generar una clave HS256 compatible con Auth y Gateway (32 a 47 bytes)");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), this::validateIdentity));
        return decoder;
    }

    private OAuth2TokenValidatorResult validateIdentity(Jwt jwt) {
        Object userId = jwt.getClaims().get("userId");
        if (jwt.getExpiresAt() != null && userId instanceof Number number) {
            try {
                // Acepta Integer/Long/BigInteger/etc. sin truncar fracciones ni desbordar Long.
                if (new BigDecimal(number.toString()).longValueExact() > 0) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (IllegalArgumentException | ArithmeticException e) {
                // Claim numérico inválido: no autenticar.
            }
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "El token no contiene una identidad o expiración válida", null));
    }
}
