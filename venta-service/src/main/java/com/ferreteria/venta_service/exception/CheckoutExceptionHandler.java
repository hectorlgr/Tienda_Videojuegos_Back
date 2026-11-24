package com.ferreteria.venta_service.exception;

import com.ferreteria.venta_service.Dto.CheckoutResponse;
import com.ferreteria.venta_service.controller.CheckoutController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Aislado del handler heredado: nunca publica ex.getMessage(), causas ni trazas. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CheckoutController.class)
public class CheckoutExceptionHandler {
    @ExceptionHandler(CheckoutHttpException.class)
    public ResponseEntity<CheckoutResponse> entrada(CheckoutHttpException ex) {
        return error(ex.getStatus(), ex.getCodigo());
    }

    @ExceptionHandler(PreparacionCheckoutException.class)
    public ResponseEntity<CheckoutResponse> preparacion(PreparacionCheckoutException ex) {
        return switch (ex.getCodigo()) {
            case CONDICIONES_NO_ACEPTADAS, ENTREGA_REQUERIDA, DESPACHO_SUPERA_LIMITE,
                    PRODUCTO_INEXISTENTE_O_NO_PUBLICADO, PRODUCTO_NO_DISPONIBLE ->
                    error(422, ex.getCodigo().name());
            case METADATOS_INVALIDOS -> error(502, "CATALOGO_NO_INTERPRETABLE");
            default -> error(503, "CHECKOUT_NO_DISPONIBLE");
        };
    }

    @ExceptionHandler(SolicitudPedidoConflictException.class)
    public ResponseEntity<CheckoutResponse> conflicto(SolicitudPedidoConflictException ex) {
        return switch (ex.getCodigo()) {
            case CONTENIDO_DISTINTO, CHECKOUT_HISTORICO_AUSENTE -> error(409, "CONFLICTO_IDEMPOTENCIA");
            default -> ResponseEntity.accepted().contentType(MediaType.APPLICATION_JSON).header("Retry-After", "5")
                    .body(CheckoutResponse.sinPedido("PENDIENTE", "REINTENTAR_MISMA_CLAVE", false));
        };
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CheckoutResponse> acceso(AccessDeniedException ex) { return error(403, "ACCESO_DENEGADO"); }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<CheckoutResponse> formato(HttpMediaTypeNotSupportedException ex) {
        return error(415, "CONTENIDO_NO_SOPORTADO");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CheckoutResponse> tecnico(Exception ex) {
        // Puede fallar después de un commit o de aplicar stock. No afirmar rechazo.
        return error(503, "RESULTADO_NO_CONFIRMADO_REINTENTAR_MISMA_CLAVE");
    }

    private ResponseEntity<CheckoutResponse> error(int status, String codigo) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(CheckoutResponse.sinPedido("ERROR", codigo, false));
    }
}
