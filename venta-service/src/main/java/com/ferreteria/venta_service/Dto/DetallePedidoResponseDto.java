package com.ferreteria.venta_service.Dto;

import com.ferreteria.venta_service.model.CategoriaProductoPedido;
import com.ferreteria.venta_service.model.DetallePedido;
import com.ferreteria.venta_service.model.FormatoProductoPedido;

public record DetallePedidoResponseDto(
        Long productoId, String sku, String nombre, CategoriaProductoPedido categoria,
        FormatoProductoPedido formato, String plataforma, Integer cantidad,
        Long precioUnitario, Long subtotal) {

    public static DetallePedidoResponseDto from(DetallePedido detalle) {
        return new DetallePedidoResponseDto(detalle.getProductoId(), detalle.getSku(), detalle.getNombre(),
                detalle.getCategoria(), detalle.getFormato(), detalle.getPlataforma(),
                detalle.getCantidad(), detalle.getPrecioUnitario(), detalle.getSubtotal());
    }
}
