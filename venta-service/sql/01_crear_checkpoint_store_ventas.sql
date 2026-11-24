-- Etapa 1: ejecutar manualmente con una cuenta administradora en MySQL 8.4.
-- Base NUEVA. Sin IF NOT EXISTS: si ya existe, detenerse y revisar su esquema.
-- No ejecutar sobre bases heredadas ni continuar tras un error.
-- DDL hace commit implicito; revisar lo aplicado antes de intentar repetir.
CREATE DATABASE checkpoint_store_ventas
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE checkpoint_store_ventas.ventas (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    fecha_venta DATETIME(6) NOT NULL,
    total INT NOT NULL,
    costo_despacho INT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE checkpoint_store_ventas.detalle_ventas (
    id BIGINT NOT NULL AUTO_INCREMENT,
    venta_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario INT NOT NULL,
    subtotal INT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_detalle_ventas_venta_id (venta_id),
    CONSTRAINT fk_venta FOREIGN KEY (venta_id)
        REFERENCES checkpoint_store_ventas.ventas (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

-- Sin INSERT, pedidos, despachos, pagos ni referencias a tablas de otros servicios.
-- La cascada de Venta.detalles es JPA; no se agrega ON DELETE CASCADE en MySQL.
