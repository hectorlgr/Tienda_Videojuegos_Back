package com.ferreteria.catalogo_service.controller;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import com.ferreteria.catalogo_service.config.StockServiceSecurityConfig;
import com.ferreteria.catalogo_service.Dto.*;
import com.ferreteria.catalogo_service.exception.*;
import com.ferreteria.catalogo_service.model.*;
import com.ferreteria.catalogo_service.service.OperacionStockService;

@Hidden
@RestController
@RequestMapping("/internal/stock/operaciones")
@RequiredArgsConstructor
public class OperacionStockController {
    private final OperacionStockService service;
    private final StockSolicitudHttpReader reader;

    @PostMapping("/{operacionId}/aplicar")
    public ResponseEntity<OperacionStockResponseDto> aplicar(@PathVariable String operacionId,
            Authentication authentication, HttpServletRequest request) throws IOException {
        String id = uuid(operacionId);
        ConsumidorStock consumidor = consumidor(authentication);
        var solicitud = reader.leer(request);
        // Sin transacción externa: A confirma antes de invocar B.
        service.registrar(id, consumidor, solicitud);
        var resultado = service.aplicar(id, consumidor);
        int status = switch (resultado.estado()) {
            case APLICADA -> 200;
            case PENDIENTE -> 202;
            case RECHAZADA, REVERTIDA, CANCELADA -> 409;
        };
        var response = ResponseEntity.status(status)
                .location(URI.create("/internal/stock/operaciones/" + id));
        if (status == 202) response.header("Retry-After", "2");
        return response.body(OperacionStockResponseDto.from(resultado));
    }

    @GetMapping("/{operacionId}")
    public OperacionStockResponseDto consultar(@PathVariable String operacionId, Authentication authentication) {
        return OperacionStockResponseDto.from(service.consultar(uuid(operacionId), consumidor(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("OPERACION_INEXISTENTE")));
    }

    @PostMapping("/{operacionId}/revertir")
    public OperacionStockResponseDto revertir(@PathVariable String operacionId,
            Authentication authentication, HttpServletRequest request) throws IOException {
        String id = uuid(operacionId);
        ConsumidorStock consumidor = consumidor(authentication);
        return OperacionStockResponseDto.from(service.revertir(id, consumidor, reader.leer(request)));
    }

    private String uuid(String id) {
        if (!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new BadRequestException("UUID_INVALIDO");
        }
        return UUID.fromString(id).toString();
    }

    private ConsumidorStock consumidor(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()
                && StockServiceSecurityConfig.SUBJECT.equals(token.getToken().getSubject())
                && token.getAuthorities().stream().anyMatch(a ->
                        StockServiceSecurityConfig.AUTHORITY.equals(a.getAuthority()))) {
            return ConsumidorStock.VENTA_SERVICE;
        }
        throw new AccessDeniedException("Identidad de servicio requerida");
    }
}
