package com.ferreteria.catalogo_service.Dto;

import java.time.Instant;
import com.ferreteria.catalogo_service.model.DatosOperacionStock;
import com.ferreteria.catalogo_service.model.EstadoOperacionStock;

/** Proyección explícita: no publica entidad, consumidor, solicitud ni hash internos. */
public record OperacionStockResponseDto(String operacionId, int versionContrato,
        EstadoOperacionStock estado, String codigo, DatosOperacionStock.Aplicacion snapshot,
        Instant fechaCreacion, Instant fechaActualizacion, Instant fechaAplicacion, Instant fechaCierre) {
    public static OperacionStockResponseDto from(DatosOperacionStock.Resultado r) {
        String codigo = switch (r.estado()) {
            case RECHAZADA -> r.codigoRechazo();
            case CANCELADA -> "OPERACION_CANCELADA";
            case REVERTIDA -> "OPERACION_REVERTIDA";
            case PENDIENTE -> "OPERACION_PENDIENTE";
            case APLICADA -> "OPERACION_APLICADA";
        };
        return new OperacionStockResponseDto(r.operacionId(), r.versionContrato(), r.estado(), codigo,
                r.resultadoAplicacion(), r.fechaCreacion(), r.fechaActualizacion(),
                r.fechaAplicacion(), r.fechaCierre());
    }
}
