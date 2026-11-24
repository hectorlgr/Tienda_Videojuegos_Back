package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.exception.PreparacionCheckoutException;
import com.ferreteria.venta_service.model.DatosCheckoutSolicitud;
import com.ferreteria.venta_service.stock.CatalogoProductosClient;
import com.ferreteria.venta_service.stock.ContratoStock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.ferreteria.venta_service.exception.PreparacionCheckoutException.Codigo.*;

/** Preparación comercial de una intención NUEVA; el servicio recupera reintentos antes. */
@Component
public class PoliticaCheckoutCatalogo implements PoliticaCheckout {
    private final CatalogoProductosClient productos;
    private final String versionCondiciones;

    public PoliticaCheckoutCatalogo(CatalogoProductosClient productos,
            @Value("${checkout.version-condiciones:}") String versionCondiciones) {
        this.productos = productos;
        this.versionCondiciones = versionCondiciones;
    }

    @Override
    public Preparacion preparar(ContratoStock.Solicitud solicitud, DatosCheckoutSolicitud.Entrada entrada) {
        // No inventar ni aceptar arbitrariamente una versión de condiciones enviada por el comprador.
        if (versionCondiciones.isBlank() || versionCondiciones.length() > 64) {
            throw new PreparacionCheckoutException(CONDICIONES_NO_CONFIGURADAS);
        }
        if (!versionCondiciones.equals(entrada.aceptacion().versionCondiciones())) {
            throw new PreparacionCheckoutException(CONDICIONES_NO_ACEPTADAS);
        }
        boolean fisico = false;
        boolean digital = false;
        for (var p : productos.consultar(solicitud)) {
            if (DespachoFijoV1.clasificarProducto(p.categoria(), p.formato()) == Composicion.DIGITAL) digital = true;
            else fisico = true;
        }
        var composicion = fisico ? (digital ? Composicion.MIXTA : Composicion.FISICA) : Composicion.DIGITAL;
        if (fisico && entrada.entrega() == null) throw new PreparacionCheckoutException(ENTREGA_REQUERIDA);
        var contexto = DespachoFijoV1.contexto(composicion);
        if (contexto.costoDespacho() > entrada.limiteTotalClp()) {
            throw new PreparacionCheckoutException(DESPACHO_SUPERA_LIMITE);
        }
        return new Preparacion(composicion, contexto);
    }
}
