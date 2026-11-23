package com.ferreteria.venta_service.Dto;

import com.ferreteria.venta_service.model.EstadoSolicitudPedido;
import com.ferreteria.venta_service.model.SolicitudPedido;
import com.ferreteria.venta_service.stock.ContratoStock;
import java.time.Instant;
import java.util.UUID;

/** Vista interna inmutable, no contrato HTTP ni entidad JPA administrada. */
public record SolicitudPedidoInterna(Long id, Long compradorId, UUID claveIdempotencia,
        int versionContrato, ContratoStock.Solicitud solicitud, String hashSolicitud,
        UUID operacionStockId, EstadoSolicitudPedido estado, Long version,
        UUID tokenReclamacion, Instant bloqueoHasta, Instant fechaCreacion, Instant fechaActualizacion) {
    public static SolicitudPedidoInterna from(SolicitudPedido s) {
        return new SolicitudPedidoInterna(s.getId(), s.getCompradorId(),
                UUID.fromString(s.getClaveIdempotencia()), s.getVersionContrato(), s.getSolicitud(),
                s.getHashSolicitud(), UUID.fromString(s.getOperacionStockId()), s.getEstado(), s.getVersion(),
                s.getTokenReclamacion() == null ? null : UUID.fromString(s.getTokenReclamacion()),
                s.getBloqueoHasta(), s.getFechaCreacion(), s.getFechaActualizacion());
    }
}
