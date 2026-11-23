package com.ferreteria.venta_service.exception;

/** Rechazo anterior al INSERT y a cualquier operación de stock. */
public class PreparacionCheckoutException extends RuntimeException {
    public enum Codigo { POLITICA_NO_CONFIGURADA, ENTREGA_REQUERIDA, DESPACHO_SUPERA_LIMITE }
    private final Codigo codigo;

    public PreparacionCheckoutException(Codigo codigo) {
        super(codigo.name());
        this.codigo = codigo;
    }

    public Codigo getCodigo() { return codigo; }
}
