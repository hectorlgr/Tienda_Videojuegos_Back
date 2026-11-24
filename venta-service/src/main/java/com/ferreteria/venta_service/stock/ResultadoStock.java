package com.ferreteria.venta_service.stock;

import java.util.UUID;

/** No transporta excepciones, headers, cuerpos crudos ni credenciales. */
public record ResultadoStock(UUID operacionId, Tipo tipo, Integer httpStatus,
        String codigo, ContratoStock.Operacion operacion) {
    public enum Tipo {
        APLICADA, PENDIENTE, RECHAZADA, REVERTIDA, CANCELADA,
        INEXISTENTE, CONFLICTO_IDEMPOTENCIA, CONFLICTO_REVERSION,
        AUTENTICACION_INTERNA, SOLICITUD_INVALIDA,
        RESULTADO_INCIERTO, CATALOGO_NO_DISPONIBLE, ERROR_TECNICO
    }

    /** Un resultado sin snapshot/estado confirmado nunca prueba ausencia de efectos. */
    public boolean requiereReconciliacion() {
        return switch (tipo) {
            case APLICADA, RECHAZADA, REVERTIDA, CANCELADA -> false;
            default -> true;
        };
    }
}
