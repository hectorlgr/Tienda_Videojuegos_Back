package com.ferreteria.venta_service.model;

/** Estado del trabajo de coordinación; no describe el pedido ni el stock remoto. */
public enum EstadoSolicitudPedido {
    PENDIENTE,
    RECONCILIACION_PENDIENTE,
    RESUELTA
}
