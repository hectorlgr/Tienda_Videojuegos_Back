package com.ferreteria.catalogo_service.model;

/**
 * Validación de altas y ediciones completas, junto con Default.
 * No se activa automáticamente en JPA: movimientos de stock y baja lógica
 * deben seguir funcionando con registros antiguos aún incompletos.
 */
public interface ProductoCompleto {
}
