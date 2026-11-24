package com.ferreteria.venta_service.Dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ferreteria.venta_service.model.EstadoPedido;

/** No expone solicitud interna, UUID stock, claim, hashes ni diagnóstico remoto. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CheckoutResponse(String resultado, String codigo, Long pedidoId,
        EstadoPedido estadoPedido, boolean terminal) {
    public static CheckoutResponse sinPedido(String resultado, String codigo, boolean terminal) {
        return new CheckoutResponse(resultado, codigo, null, null, terminal);
    }
}
