package com.ferreteria.venta_service.model;

import com.ferreteria.venta_service.stock.ContratoStock;

/** Valores JSON inmutables de persistencia; no son endpoints ni políticas de precios. */
public final class DatosCheckoutSolicitud {
    private DatosCheckoutSolicitud() {}

    public record Entrega(String destinatario, String telefono, String direccion,
            String region, String comuna, String referencia) {
        public Entrega {
            texto(destinatario, 255);
            texto(telefono, 30);
            texto(direccion, 255);
            texto(region, 100);
            texto(comuna, 100);
            if (referencia != null) texto(referencia, 500);
        }
    }

    public record Aceptacion(String versionCondiciones, boolean aceptaPrecioVigenteHastaLimite) {
        public Aceptacion {
            texto(versionCondiciones, 64);
            exigir(aceptaPrecioVigenteHastaLimite);
        }
    }

    public record Entrada(Entrega entrega, Long limiteTotalClp, Aceptacion aceptacion) {
        public Entrada {
            exigir(limiteTotalClp != null && limiteTotalClp > 0 && aceptacion != null);
        }
    }

    public record ContextoServidor(String politicaDespacho, int versionPoliticaDespacho,
            Long costoDespacho, int versionContratoComercial) {
        public ContextoServidor {
            texto(politicaDespacho, 64);
            exigir(versionPoliticaDespacho > 0 && costoDespacho != null && costoDespacho >= 0
                    && versionContratoComercial == 1);
        }
    }

    public record Checkout(int versionContrato, Entrada entrada, ContextoServidor contextoServidor) {
        public Checkout {
            exigir(versionContrato == 1 && entrada != null && contextoServidor != null);
        }
    }

    public enum TipoResultado {
        PEDIDO_CREADO, RECHAZO_DEFINITIVO, COMPENSACION_CONFIRMADA,
        CIERRE_PREVENTIVO_CONFIRMADO, RECHAZO_LOCAL
    }

    public record Resultado(int versionContrato, TipoResultado tipo, String codigo,
            ContratoStock.Estado estadoRemoto, Long pedidoId) {
        public Resultado {
            exigir(versionContrato == 1 && tipo != null && codigo != null
                    && codigo.matches("[A-Z][A-Z0-9_]{0,63}"));
            boolean coherente = switch (tipo) {
                case PEDIDO_CREADO -> estadoRemoto == ContratoStock.Estado.APLICADA
                        && pedidoId != null && pedidoId > 0;
                case RECHAZO_DEFINITIVO -> estadoRemoto == ContratoStock.Estado.RECHAZADA && pedidoId == null;
                case COMPENSACION_CONFIRMADA -> estadoRemoto == ContratoStock.Estado.REVERTIDA && pedidoId == null;
                case CIERRE_PREVENTIVO_CONFIRMADO -> estadoRemoto == ContratoStock.Estado.CANCELADA && pedidoId == null;
                case RECHAZO_LOCAL -> estadoRemoto == null && pedidoId == null;
            };
            exigir(coherente);
        }
    }

    private static void texto(String valor, int maximo) {
        exigir(valor != null && !valor.isBlank() && valor.codePointCount(0, valor.length()) <= maximo);
    }

    private static void exigir(boolean condicion) {
        if (!condicion) throw new IllegalArgumentException("Contrato persistido de checkout inválido");
    }
}
