package com.ferreteria.catalogo_service.exception;

import java.util.Map;
import java.util.Set;
import com.ferreteria.catalogo_service.controller.OperacionStockController;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Order(-1)
@RestControllerAdvice(assignableTypes = OperacionStockController.class)
public class StockHttpExceptionHandler {
    private static final Set<String> CONFLICTOS = Set.of("OPERACION_CONSUMIDOR_DISTINTO",
            "OPERACION_CONTENIDO_DISTINTO", "REVERSION_PRODUCTO_INEXISTENTE", "REVERSION_STOCK_FUERA_DE_RANGO");

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<?> invalida(BadRequestException ex) {
        return error(400, "UUID_INVALIDO".equals(ex.getMessage()) ? "UUID_INVALIDO" : "SOLICITUD_INVALIDA");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> inexistente() { return error(404, "OPERACION_INEXISTENTE"); }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<?> conflicto(ConflictException ex) {
        return error(409, CONFLICTOS.contains(ex.getMessage()) ? ex.getMessage() : "OPERACION_CONFLICTO");
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> denegado() { return error(403, "SERVICIO_SIN_PERMISO"); }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> formato(ResponseStatusException ex) {
        return error(ex.getStatusCode().value(), ex.getStatusCode().value() == 413
                ? "SOLICITUD_DEMASIADO_GRANDE" : "FORMATO_NO_SOPORTADO");
    }

    @ExceptionHandler({org.springframework.dao.TransientDataAccessException.class,
            org.springframework.dao.DataAccessResourceFailureException.class,
            org.springframework.transaction.CannotCreateTransactionException.class,
            jakarta.persistence.LockTimeoutException.class, jakarta.persistence.PessimisticLockException.class})
    public ResponseEntity<?> temporal() {
        return ResponseEntity.status(503).header("Retry-After", "2")
                .body(Map.of("codigo", "STOCK_TEMPORALMENTE_NO_DISPONIBLE",
                        "mensaje", "Consulte la operación con el mismo UUID antes de reintentar."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> tecnico() { return error(500, "ERROR_TECNICO_STOCK"); }

    private ResponseEntity<?> error(int status, String codigo) {
        return ResponseEntity.status(status).body(Map.of("codigo", codigo));
    }
}
