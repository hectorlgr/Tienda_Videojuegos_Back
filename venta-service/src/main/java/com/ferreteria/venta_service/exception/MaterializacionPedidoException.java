package com.ferreteria.venta_service.exception;

/** Fallos locales tipados; ninguno demuestra una restitución remota. */
public class MaterializacionPedidoException extends RuntimeException {
    public enum Codigo {
        SNAPSHOT_INVALIDO, CHECKOUT_AUSENTE, COMPENSACION_PENDIENTE,
        ENTREGA_FISICA_AUSENTE, DESPACHO_DIGITAL_INVALIDO,
        TOTAL_NO_ACEPTADO, TOTAL_FUERA_DE_RANGO, PEDIDO_INCONSISTENTE,
        PERSISTENCIA_TRANSITORIA
    }

    private final Codigo codigo;

    public MaterializacionPedidoException(Codigo codigo) {
        super(codigo.name());
        this.codigo = codigo;
    }

    public MaterializacionPedidoException(Codigo codigo, Throwable causa) {
        super(codigo.name(), causa);
        this.codigo = codigo;
    }

    public Codigo getCodigo() { return codigo; }

    /** Señal al futuro coordinador, NO una decisión durable ni autorización de HTTP. */
    public boolean requiereCompensacion() {
        return switch (codigo) {
            case ENTREGA_FISICA_AUSENTE, DESPACHO_DIGITAL_INVALIDO,
                    TOTAL_NO_ACEPTADO, TOTAL_FUERA_DE_RANGO -> true;
            default -> false;
        };
    }
}
