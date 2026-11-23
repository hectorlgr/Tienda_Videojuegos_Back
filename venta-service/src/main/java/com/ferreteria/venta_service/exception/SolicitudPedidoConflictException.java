package com.ferreteria.venta_service.exception;

/** Conflicto interno tipado; su traducción HTTP queda para una etapa posterior. */
public class SolicitudPedidoConflictException extends RuntimeException {
    public enum Codigo {
        CONTENIDO_DISTINTO, CHECKOUT_HISTORICO_AUSENTE,
        RECLAMACION_VIGENTE, TOKEN_NO_VIGENTE, SOLICITUD_RESUELTA
    }

    private final Codigo codigo;

    public SolicitudPedidoConflictException(Codigo codigo) {
        super(codigo.name());
        this.codigo = codigo;
    }

    public Codigo getCodigo() {
        return codigo;
    }
}
