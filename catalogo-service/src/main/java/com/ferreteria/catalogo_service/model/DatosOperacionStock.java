package com.ferreteria.catalogo_service.model;

import java.time.Instant;
import java.util.List;

/** Valores inmutables internos; no son contratos de controladores HTTP. */
public final class DatosOperacionStock {
    private DatosOperacionStock() {}

    public record Item(Long productoId, Integer cantidad) {}

    public record Solicitud(int versionContrato, List<Item> items) {
        public Solicitud {
            // Conserva entradas inválidas hasta la validación explícita del servicio.
            if (items != null) items = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items));
        }
    }

    public record Detalle(Long productoId, String sku, String nombre,
            CategoriaProducto categoria, FormatoProducto formato, String plataforma,
            Integer cantidad, Long precioUnitario, Long subtotal) {}

    public record Aplicacion(int versionContrato, List<Detalle> items, Long totalProductos) {
        public Aplicacion {
            items = List.copyOf(items);
        }
    }

    public record Resultado(String operacionId, ConsumidorStock consumidor, int versionContrato,
            Solicitud solicitud, String hashSolicitud, EstadoOperacionStock estado,
            Aplicacion resultadoAplicacion, String codigoRechazo, Instant fechaCreacion,
            Instant fechaActualizacion, Instant fechaAplicacion, Instant fechaCierre) {}
}
