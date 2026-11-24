package com.ferreteria.venta_service.Dto;

import com.ferreteria.venta_service.model.DatosCheckoutSolicitud;
import com.ferreteria.venta_service.stock.ContratoStock;
import java.util.List;
import java.util.Objects;

/** Solo entrada del comprador; sin identidad, precios ni contexto servidor. */
public record CheckoutRequest(List<ContratoStock.Item> items, DatosCheckoutSolicitud.Entrega entrega,
        Long limiteTotalClp, DatosCheckoutSolicitud.Aceptacion aceptacion) {
    public CheckoutRequest {
        items = new ContratoStock.Solicitud(1, items).items();
        Objects.requireNonNull(aceptacion, "Aceptación requerida");
        new DatosCheckoutSolicitud.Entrada(entrega, limiteTotalClp, aceptacion);
    }

    public DatosCheckoutSolicitud.Entrada entrada() {
        return new DatosCheckoutSolicitud.Entrada(entrega, limiteTotalClp, aceptacion);
    }
}
