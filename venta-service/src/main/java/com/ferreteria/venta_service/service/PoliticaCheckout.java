package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.model.DatosCheckoutSolicitud;
import com.ferreteria.venta_service.stock.ContratoStock;
import java.util.Objects;

/**
 * Puerto exclusivo del backend, no parámetro del registro ni contrato HTTP.
 * Una implementación debe acreditar composición de TODOS los productos, aceptación
 * de la versión de condiciones y aplicabilidad de la política a la entrega.
 * Solo lógica/configuración local confiable; sin HTTP ni precios del frontend.
 * DespachoFijoV1 implementa la tarifa aprobada; no se instala un preparador hasta
 * disponer también de clasificación confiable de Catálogo previa al registro.
 */
public interface PoliticaCheckout {
    Preparacion preparar(ContratoStock.Solicitud solicitud, DatosCheckoutSolicitud.Entrada entrada);

    enum Composicion { DIGITAL, FISICA, MIXTA }

    record Preparacion(Composicion composicion, DatosCheckoutSolicitud.ContextoServidor contexto) {
        public Preparacion {
            Objects.requireNonNull(composicion);
            Objects.requireNonNull(contexto);
            if (composicion == Composicion.DIGITAL && contexto.costoDespacho() != 0) {
                throw new IllegalArgumentException("El despacho digital debe ser cero");
            }
        }
    }
}
