-- Etapa 3.1. Ejecutar MANUALMENTE con una cuenta administradora en MySQL 8.4.
-- Requiere checkpoint_store_ventas existente. NO volver a ejecutar el script 01.
-- Aditivo: solo crea pedidos y detalle_pedidos; no modifica tablas heredadas.
-- Sin IF NOT EXISTS: ante un error, detenerse e inspeccionar lo aplicado.
-- DDL hace commit implicito. No repetir ni continuar ciegamente tras un fallo.
-- Todas las fechas DATETIME(6) representan UTC, aunque MySQL no almacena su zona.

CREATE TABLE checkpoint_store_ventas.pedidos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comprador_id BIGINT NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    total_productos BIGINT NOT NULL,
    costo_despacho BIGINT NOT NULL,
    total_final BIGINT NOT NULL,
    requiere_despacho BOOLEAN NOT NULL,
    contiene_digitales BOOLEAN NOT NULL,
    entrega_destinatario VARCHAR(255) NULL,
    entrega_telefono VARCHAR(30) NULL,
    entrega_direccion VARCHAR(255) NULL,
    entrega_region VARCHAR(100) NULL,
    entrega_comuna VARCHAR(100) NULL,
    entrega_referencia VARCHAR(500) NULL,
    version BIGINT NOT NULL,
    clave_idempotencia VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    hash_solicitud VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operacion_stock_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    estado_stock VARCHAR(30) NOT NULL,
    motivo_cancelacion VARCHAR(500) NULL,
    fecha_cancelacion DATETIME(6) NULL,
    fecha_entrega_fisica DATETIME(6) NULL,
    fecha_cumplimiento_digital DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_pedido_comprador_idempotencia UNIQUE (comprador_id, clave_idempotencia),
    CONSTRAINT uq_pedido_operacion_stock UNIQUE (operacion_stock_id),
    INDEX idx_pedido_comprador_fecha (comprador_id, fecha_creacion, id),
    INDEX idx_pedido_estado_fecha (estado, fecha_creacion, id),
    INDEX idx_pedido_fecha (fecha_creacion, id),
    INDEX idx_pedido_stock_actualizacion (estado_stock, fecha_actualizacion, id),
    CONSTRAINT chk_pedido_comprador CHECK (comprador_id > 0),
    CONSTRAINT chk_pedido_version CHECK (version >= 0),
    CONSTRAINT chk_pedido_estado CHECK (CAST(estado AS BINARY) IN (
        'CREADO', 'CONFIRMADO', 'EN_PREPARACION', 'DESPACHADO', 'COMPLETADO', 'CANCELADO'
    )),
    CONSTRAINT chk_pedido_estado_stock CHECK (CAST(estado_stock AS BINARY) IN (
        'PENDIENTE', 'DESCONTADO', 'SIN_DESCUENTO', 'REVERSION_PENDIENTE', 'RESTITUIDO'
    )),
    CONSTRAINT chk_pedido_importes CHECK (
        total_productos >= 0 AND costo_despacho >= 0 AND total_final >= 0
        AND total_final = CAST(total_productos AS DECIMAL(30,0)) + costo_despacho
    ),
    CONSTRAINT chk_pedido_indicadores CHECK (
        requiere_despacho IN (0, 1) AND contiene_digitales IN (0, 1)
        AND (requiere_despacho = 1 OR contiene_digitales = 1)
    ),
    CONSTRAINT chk_pedido_entrega CHECK (
        (requiere_despacho = 1
            AND entrega_destinatario IS NOT NULL AND CHAR_LENGTH(TRIM(entrega_destinatario)) > 0
            AND entrega_telefono IS NOT NULL AND CHAR_LENGTH(TRIM(entrega_telefono)) > 0
            AND entrega_direccion IS NOT NULL AND CHAR_LENGTH(TRIM(entrega_direccion)) > 0
            AND entrega_region IS NOT NULL AND CHAR_LENGTH(TRIM(entrega_region)) > 0
            AND entrega_comuna IS NOT NULL AND CHAR_LENGTH(TRIM(entrega_comuna)) > 0)
        OR (requiere_despacho = 0 AND costo_despacho = 0
            AND entrega_destinatario IS NULL AND entrega_telefono IS NULL
            AND entrega_direccion IS NULL AND entrega_region IS NULL
            AND entrega_comuna IS NULL AND entrega_referencia IS NULL)
    ),
    CONSTRAINT chk_pedido_clave_idempotencia CHECK (
        REGEXP_LIKE(clave_idempotencia, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')
    ),
    CONSTRAINT chk_pedido_hash CHECK (REGEXP_LIKE(hash_solicitud, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_pedido_operacion_stock CHECK (
        REGEXP_LIKE(operacion_stock_id, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')
    ),
    CONSTRAINT chk_pedido_fechas CHECK (
        fecha_actualizacion >= fecha_creacion
        AND (fecha_cancelacion IS NULL OR fecha_cancelacion >= fecha_creacion)
        AND (fecha_entrega_fisica IS NULL
            OR (requiere_despacho = 1 AND fecha_entrega_fisica >= fecha_creacion))
        AND (fecha_cumplimiento_digital IS NULL
            OR (contiene_digitales = 1 AND fecha_cumplimiento_digital >= fecha_creacion))
    ),
    CONSTRAINT chk_pedido_motivo CHECK (
        motivo_cancelacion IS NULL OR CHAR_LENGTH(TRIM(motivo_cancelacion)) > 0
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE checkpoint_store_ventas.detalle_pedidos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    sku VARCHAR(64) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    categoria VARCHAR(20) NOT NULL,
    formato VARCHAR(10) NULL,
    plataforma VARCHAR(255) NULL,
    cantidad INT NOT NULL,
    precio_unitario BIGINT NOT NULL,
    subtotal BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_detalle_pedido_producto UNIQUE (pedido_id, producto_id),
    CONSTRAINT fk_detalle_pedido FOREIGN KEY (pedido_id)
        REFERENCES checkpoint_store_ventas.pedidos (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_detalle_pedido_producto CHECK (producto_id > 0),
    CONSTRAINT chk_detalle_pedido_textos CHECK (
        CHAR_LENGTH(TRIM(sku)) > 0 AND CHAR_LENGTH(TRIM(nombre)) > 0
    ),
    CONSTRAINT chk_detalle_pedido_categoria CHECK (
        CAST(categoria AS BINARY) IN ('VIDEOJUEGO', 'CONSOLA', 'ACCESORIO', 'FIGURA')
    ),
    CONSTRAINT chk_detalle_pedido_formato CHECK (
        formato IS NULL OR CAST(formato AS BINARY) IN ('FISICO', 'DIGITAL')
    ),
    CONSTRAINT chk_detalle_pedido_videojuego CHECK (
        CAST(categoria AS BINARY) <> 'VIDEOJUEGO'
        OR (formato IS NOT NULL AND plataforma IS NOT NULL
            AND CHAR_LENGTH(TRIM(plataforma)) > 0)
    ),
    CONSTRAINT chk_detalle_pedido_importes CHECK (
        cantidad > 0 AND precio_unitario > 0 AND subtotal > 0
        AND subtotal = CAST(precio_unitario AS DECIMAL(30,0)) * cantidad
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- La unicidad (pedido_id, producto_id) ya indexa la FK y la consulta de detalles.
-- No hay FK a Identidad/Catalogo, cascadas de borrado, defaults de negocio ni semillas.
-- Pendiente en servicios futuros: suma de detalles, composicion fisica/digital,
-- pedido no vacio, transiciones, autorizacion, generacion de fechas e idempotencia.
