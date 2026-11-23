package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.model.DatosCheckoutSolicitud;
import com.ferreteria.venta_service.stock.ContratoStock;
import java.util.Objects;

/**
 * Regla comercial pura aprobada. No es un bean PoliticaCheckout: falta la
 * consulta confiable de composición ANTES de registrar la intención.
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
            if (item.formato() == ContratoStock.Formato.DIGITAL) {
                if (item.categoria() != ContratoStock.Categoria.VIDEOJUEGO) {
                    throw new IllegalArgumentException("Formato digital no admitido para esta categoría");
                }
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
