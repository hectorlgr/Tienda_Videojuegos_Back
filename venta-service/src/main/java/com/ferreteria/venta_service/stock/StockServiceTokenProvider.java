package com.ferreteria.venta_service.stock;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class StockServiceTokenProvider {
    private final JwtEncoder encoder;

    public StockServiceTokenProvider(@Value("${jwt.secret:${JWT_SECRET}}") String humanSecret) {
        String secret = System.getenv("STOCK_SERVICE_JWT_SECRET");
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.equals(humanSecret)) {
            throw new IllegalArgumentException(
                    "STOCK_SERVICE_JWT_SECRET requiere al menos 32 bytes y debe diferir del secreto humano");
        }
        encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secret.getBytes(StandardCharsets.UTF_8)));
    }

    // Solo el cliente del mismo paquete consume este token. No es bean JwtEncoder público.
    String emitir() {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("checkpoint-stock-services").audience(List.of("catalogo-stock"))
                .subject("venta-service").claim("scope", "stock:operar")
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(240)).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }
}
