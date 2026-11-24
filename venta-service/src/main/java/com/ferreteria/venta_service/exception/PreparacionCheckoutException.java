package com.ferreteria.venta_service.exception;

/** Rechazo anterior al INSERT y a cualquier operación de stock. */
public class PreparacionCheckoutException extends RuntimeException {
    public enum Codigo {
        POLITICA_NO_CONFIGURADA, ENTREGA_REQUERIDA, DESPACHO_SUPERA_LIMITE,
        CONDICIONES_NO_CONFIGURADAS, CONDICIONES_NO_ACEPTADAS,
        CATALOGO_NO_DISPONIBLE, ACCESO_CATALOGO_RECHAZADO,
        PRODUCTO_INEXISTENTE_O_NO_PUBLICADO, PRODUCTO_NO_DISPONIBLE, METADATOS_INVALIDOS
    }
    private final Codigo codigo;

    public PreparacionCheckoutException(Codigo codigo) {
        super(codigo.name());
        this.codigo = codigo;
    }

    public Codigo getCodigo() { return codigo; }
}
