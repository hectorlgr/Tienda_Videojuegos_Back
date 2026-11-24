-- Etapa 4.6A.1. Ejecutar MANUALMENTE una sola vez en MySQL 8.4.
-- Requiere checkpoint_store_ventas y los scripts 01/02 ya aplicados.
-- Solo crea la tabla tecnica; no modifica tablas existentes ni permisos.
-- Sin IF NOT EXISTS: si existe, detenerse e inspeccionar SHOW CREATE TABLE.
-- DDL hace commit implicito. No se ha ejecutado desde esta implementacion.
-- Fechas DATETIME(6) en UTC; Hibernate mantiene ddl-auto=validate.

CREATE TABLE checkpoint_store_ventas.solicitudes_pedido (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comprador_id BIGINT NOT NULL,
    clave_idempotencia VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_contrato INT NOT NULL,
    solicitud JSON NOT NULL,
    hash_solicitud VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operacion_stock_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    estado VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version BIGINT NOT NULL,
    token_reclamacion VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    bloqueo_hasta DATETIME(6) NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_solicitud_comprador_clave UNIQUE (comprador_id, clave_idempotencia),
    CONSTRAINT uq_solicitud_operacion_stock UNIQUE (operacion_stock_id),
    INDEX idx_solicitud_estado_actualizacion (estado, fecha_actualizacion, id),
    INDEX idx_solicitud_estado_bloqueo (estado, bloqueo_hasta, id),
    CONSTRAINT chk_solicitud_comprador CHECK (comprador_id > 0),
    CONSTRAINT chk_solicitud_version_contrato CHECK (version_contrato = 1),
    CONSTRAINT chk_solicitud_version CHECK (version >= 0),
    CONSTRAINT chk_solicitud_clave CHECK (
        REGEXP_LIKE(clave_idempotencia,
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')
    ),
    CONSTRAINT chk_solicitud_hash CHECK (
        REGEXP_LIKE(hash_solicitud, '^[0-9a-f]{64}$', 'c')
    ),
    CONSTRAINT chk_solicitud_operacion CHECK (
        REGEXP_LIKE(operacion_stock_id,
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')
    ),
    CONSTRAINT chk_solicitud_estado CHECK (
        estado IN ('PENDIENTE', 'RECONCILIACION_PENDIENTE', 'RESUELTA')
    ),
    CONSTRAINT chk_solicitud_json CHECK (
        JSON_SCHEMA_VALID('{
            "type":"object",
            "required":["versionContrato","items"],
            "additionalProperties":false,
            "properties":{
                "versionContrato":{"type":"integer","enum":[1]},
                "items":{
                    "type":"array","minItems":1,"maxItems":100,
                    "items":{
                        "type":"object",
                        "required":["productoId","cantidad"],
                        "additionalProperties":false,
                        "properties":{
                            "productoId":{"type":"integer","minimum":1,"maximum":9223372036854775807},
                            "cantidad":{"type":"integer","minimum":1,"maximum":2147483647}
                        }
                    }
                }
            }
        }', solicitud)
    ),
    CONSTRAINT chk_solicitud_token CHECK (
        token_reclamacion IS NULL OR REGEXP_LIKE(token_reclamacion,
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')
    ),
    CONSTRAINT chk_solicitud_reclamacion CHECK (
        (token_reclamacion IS NULL AND bloqueo_hasta IS NULL)
        OR (token_reclamacion IS NOT NULL AND bloqueo_hasta IS NOT NULL)
    ),
    CONSTRAINT chk_solicitud_resuelta CHECK (
        estado <> 'RESUELTA' OR (token_reclamacion IS NULL AND bloqueo_hasta IS NULL)
    ),
    CONSTRAINT chk_solicitud_fechas CHECK (
        fecha_actualizacion >= fecha_creacion
        AND (bloqueo_hasta IS NULL OR bloqueo_hasta > fecha_creacion)
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- No se exige bloqueo_hasta > fecha_actualizacion: un arrendamiento puede vencer
-- y debe seguir siendo recuperable. El coordinador validara vigencia y token.
-- Orden/duplicados, hash del contenido y transiciones se controlan en Java.
-- La solicitud no incluye precios, entrega ni snapshots. Sin FK entre servicios.
