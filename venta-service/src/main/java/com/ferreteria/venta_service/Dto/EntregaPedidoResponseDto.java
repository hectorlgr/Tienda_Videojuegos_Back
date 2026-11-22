package com.ferreteria.venta_service.Dto;

import com.ferreteria.venta_service.model.EntregaPedido;

public record EntregaPedidoResponseDto(
        String destinatario, String telefono, String direccion,
        String region, String comuna, String referencia) {

    public static EntregaPedidoResponseDto from(EntregaPedido entrega) {
        if (entrega == null) return null;
        return new EntregaPedidoResponseDto(entrega.getDestinatario(), entrega.getTelefono(),
                entrega.getDireccion(), entrega.getRegion(), entrega.getComuna(), entrega.getReferencia());
    }
}
