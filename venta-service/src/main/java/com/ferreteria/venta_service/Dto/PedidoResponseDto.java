package com.ferreteria.venta_service.Dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ferreteria.venta_service.model.DetallePedido;
import com.ferreteria.venta_service.model.EstadoPedido;
import com.ferreteria.venta_service.model.Pedido;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.hateoas.server.core.Relation;

@Relation(itemRelation = "pedido", collectionRelation = "pedidos")
public record PedidoResponseDto(
        Long id,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant fechaCreacion,
        EstadoPedido estado,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long compradorId,
        String moneda, Long totalProductos, Long costoDespacho, Long totalFinal,
        Boolean requiereDespacho, Boolean contieneDigitales,
        EntregaPedidoResponseDto entrega, List<DetallePedidoResponseDto> detalles) {

    // Invocar dentro de la transacción de lectura, con detalles ya cargados.
    public static PedidoResponseDto from(Pedido pedido, boolean administrativo) {
        return new PedidoResponseDto(pedido.getId(), pedido.getFechaCreacion(), pedido.getEstado(),
                administrativo ? pedido.getCompradorId() : null, "CLP",
                pedido.getTotalProductos(), pedido.getCostoDespacho(), pedido.getTotalFinal(),
                pedido.getRequiereDespacho(), pedido.getContieneDigitales(),
                Boolean.TRUE.equals(pedido.getRequiereDespacho())
                        ? EntregaPedidoResponseDto.from(pedido.getEntrega()) : null,
                pedido.getDetalles().stream()
                        .sorted(Comparator.comparing(DetallePedido::getId))
                        .map(DetallePedidoResponseDto::from).toList());
    }
}
