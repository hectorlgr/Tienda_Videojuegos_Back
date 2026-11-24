package com.ferreteria.venta_service.stock;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Contrato HTTP propio de Ventas; no depende de entidades de Catálogo o Pedido. */
public final class ContratoStock {
    private ContratoStock() {}

    public enum Estado { PENDIENTE, APLICADA, RECHAZADA, REVERTIDA, CANCELADA }
    public enum Categoria { VIDEOJUEGO, CONSOLA, ACCESORIO, FIGURA }
    public enum Formato { FISICO, DIGITAL }

    public record Item(Long productoId, Integer cantidad) {
        public Item {
            exigir(productoId != null && productoId > 0 && cantidad != null && cantidad > 0);
        }
    }

    public record Solicitud(int versionContrato, List<Item> items) {
        public Solicitud {
            exigir(versionContrato == 1 && items != null && !items.isEmpty() && items.size() <= 100);
            items = List.copyOf(items);
            Set<Long> ids = new HashSet<>();
            for (Item item : items) exigir(ids.add(item.productoId()));
        }
    }

    public record Detalle(Long productoId, String sku, String nombre, Categoria categoria,
            Formato formato, String plataforma, Integer cantidad, Long precioUnitario, Long subtotal) {
        public Detalle {
            exigir(productoId != null && productoId > 0 && cantidad != null && cantidad > 0
                    && sku != null && !sku.isBlank() && nombre != null && !nombre.isBlank()
                    && categoria != null && precioUnitario != null && precioUnitario > 0
                    && subtotal != null && subtotal > 0);
            exigir(subtotal == Math.multiplyExact(precioUnitario, cantidad.longValue()));
            if (categoria == Categoria.VIDEOJUEGO) {
                exigir(formato != null && plataforma != null && !plataforma.isBlank());
            }
        }
    }

    public record Snapshot(int versionContrato, List<Detalle> items, Long totalProductos) {
        public Snapshot {
            exigir(versionContrato == 1 && items != null && !items.isEmpty() && items.size() <= 100
                    && totalProductos != null && totalProductos > 0);
            items = List.copyOf(items);
            Set<Long> ids = new HashSet<>();
            long total = 0;
            for (Detalle item : items) {
                exigir(ids.add(item.productoId()));
                total = Math.addExact(total, item.subtotal());
            }
            exigir(total == totalProductos);
        }
    }

    public record Operacion(UUID operacionId, int versionContrato, Estado estado, String codigo,
            Snapshot snapshot, Instant fechaCreacion, Instant fechaActualizacion,
            Instant fechaAplicacion, Instant fechaCierre) {
        public Operacion {
            exigir(operacionId != null && versionContrato == 1 && estado != null
                    && codigo != null && fechaCreacion != null && fechaActualizacion != null);
            exigir(!fechaActualizacion.isBefore(fechaCreacion));
            boolean aplicada = estado == Estado.APLICADA || estado == Estado.REVERTIDA;
            boolean cerrada = estado == Estado.RECHAZADA || estado == Estado.REVERTIDA || estado == Estado.CANCELADA;
            exigir(aplicada == (snapshot != null) && aplicada == (fechaAplicacion != null)
                    && cerrada == (fechaCierre != null));
            if (fechaAplicacion != null) exigir(!fechaAplicacion.isBefore(fechaCreacion)
                    && !fechaAplicacion.isAfter(fechaActualizacion));
            if (fechaCierre != null) exigir(fechaCierre.equals(fechaActualizacion));
            if (estado == Estado.RECHAZADA) {
                exigir(Set.of("PRODUCTO_INEXISTENTE", "PRODUCTO_DESHABILITADO", "STOCK_INSUFICIENTE",
                        "METADATOS_INVALIDOS", "PRECIO_INVALIDO", "IMPORTE_FUERA_DE_RANGO").contains(codigo));
            } else {
                exigir(Objects.equals(codigo, "OPERACION_" + estado.name()));
            }
        }
    }

    private static void exigir(boolean condicion) {
        if (!condicion) throw new IllegalArgumentException("Contrato de stock inválido");
    }
}
