package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.model.DatosCheckoutSolicitud;
import com.ferreteria.venta_service.stock.ContratoStock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.StringJoiner;

/** Sobre v1: claves lexicográficas, UTF-8, null explícito, sin normalizar textos. */
final class HashCheckout {
    private HashCheckout() {}

    static String calcular(ContratoStock.Solicitud solicitud, DatosCheckoutSolicitud.Entrada entrada) {
        StringJoiner items = new StringJoiner(",");
        solicitud.items().stream().sorted(Comparator.comparing(ContratoStock.Item::productoId))
                .forEach(i -> items.add("{\"cantidad\":" + i.cantidad() + ",\"productoId\":" + i.productoId() + "}"));
        var a = entrada.aceptacion();
        var e = entrada.entrega();
        String entrega = e == null ? "null" : "{\"comuna\":" + texto(e.comuna())
                + ",\"destinatario\":" + texto(e.destinatario()) + ",\"direccion\":" + texto(e.direccion())
                + ",\"referencia\":" + texto(e.referencia()) + ",\"region\":" + texto(e.region())
                + ",\"telefono\":" + texto(e.telefono()) + "}";
        String json = "{\"entrada\":{\"aceptacion\":{\"aceptaPrecioVigenteHastaLimite\":"
                + a.aceptaPrecioVigenteHastaLimite() + ",\"versionCondiciones\":" + texto(a.versionCondiciones())
                + "},\"entrega\":" + entrega + ",\"limiteTotalClp\":" + entrada.limiteTotalClp()
                + "},\"solicitud\":{\"items\":[" + items + "],\"versionContrato\":1},\"versionContratoCheckout\":1}";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private static String texto(String valor) {
        if (valor == null) return "null";
        StringBuilder salida = new StringBuilder("\"");
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            if (c == '"' || c == '\\') salida.append('\\').append(c);
            else if (c < 0x20) {
                salida.append("\\u00").append(Character.forDigit(c >> 4, 16))
                        .append(Character.forDigit(c & 15, 16));
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 >= valor.length() || !Character.isLowSurrogate(valor.charAt(i + 1))) {
                    throw new IllegalArgumentException("Texto Unicode inválido");
                }
                salida.append(c).append(valor.charAt(++i));
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Texto Unicode inválido");
            } else salida.append(c);
        }
        return salida.append('"').toString();
    }
}
