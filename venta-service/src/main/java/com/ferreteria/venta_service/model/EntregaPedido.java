package com.ferreteria.venta_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Snapshot opcional de entrega. Vive en pedidos, no crea otra tabla.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class EntregaPedido {
    @Column(name = "entrega_destinatario", length = 255)
    private String destinatario;

    @Column(name = "entrega_telefono", length = 30)
    private String telefono;

    @Column(name = "entrega_direccion", length = 255)
    private String direccion;

    @Column(name = "entrega_region", length = 100)
    private String region;

    @Column(name = "entrega_comuna", length = 100)
    private String comuna;

    @Column(name = "entrega_referencia", length = 500)
    private String referencia;
}
