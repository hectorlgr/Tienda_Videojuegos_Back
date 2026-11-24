package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.model.DatosCheckoutSolicitud;
import com.ferreteria.venta_service.stock.ContratoStock;
import java.util.Objects;

/**
 * Regla comercial pura aprobada, compartida por prelectura y snapshot definitivo.
 */
public final class DespachoFijoV1 {
    public static final String POLITICA = "DESPACHO_FIJO_V1";
    public static final int VERSION = 1;
    public static final long COSTO_FISICO_CLP = 3990L;

    private DespachoFijoV1() {}

    /** Solo datos obtenidos por backend desde Catálogo, nunca indicadores de React. */
    public static PoliticaCheckout.Composicion clasificar(ContratoStock.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "Snapshot de Catálogo requerido");
        boolean fisico = false;
        boolean digital = false;
        for (var item : snapshot.items()) {
            if (clasificarProducto(item.categoria(), item.formato()) == PoliticaCheckout.Composicion.DIGITAL) {
                digital = true;
            } else {
                // ContratoStock exige formato para videojuegos. NULL en las
                // demás categorías no significa digital.
                fisico = true;
            }
        }
        return fisico ? (digital ? PoliticaCheckout.Composicion.MIXTA : PoliticaCheckout.Composicion.FISICA)
                : PoliticaCheckout.Composicion.DIGITAL;
    }

    public static PoliticaCheckout.Composicion clasificarProducto(ContratoStock.Categoria categoria,
            ContratoStock.Formato formato) {
        Objects.requireNonNull(categoria, "Categoría requerida");
        if (categoria == ContratoStock.Categoria.VIDEOJUEGO && formato == null) {
            throw new IllegalArgumentException("Videojuego sin formato");
        }
        if (formato == ContratoStock.Formato.DIGITAL) {
            if (categoria != ContratoStock.Categoria.VIDEOJUEGO) {
                throw new IllegalArgumentException("Formato digital no admitido para esta categoría");
            }
            return PoliticaCheckout.Composicion.DIGITAL;
        }
        return PoliticaCheckout.Composicion.FISICA;
    }

    /** Importe por pedido; independiente del número de productos y unidades. */
    public static long costo(PoliticaCheckout.Composicion composicion) {
        return switch (Objects.requireNonNull(composicion, "Composición confiable requerida")) {
            case DIGITAL -> 0L;
            case FISICA, MIXTA -> COSTO_FISICO_CLP;
        };
    }

    public static DatosCheckoutSolicitud.ContextoServidor contexto(PoliticaCheckout.Composicion composicion) {
        return new DatosCheckoutSolicitud.ContextoServidor(POLITICA, VERSION, costo(composicion), 1);
    }
}
