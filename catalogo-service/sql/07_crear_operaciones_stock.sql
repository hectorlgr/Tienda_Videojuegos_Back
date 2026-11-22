-- Etapa 4.2. Ejecutar MANUALMENTE en MySQL 8.4. No ejecutado por el agente.
-- Aditivo: solo crea operaciones_stock. No modifica productos ni sus índices.
-- Sin IF NOT EXISTS: si ya existe, detenerse y revisar SHOW CREATE TABLE.
-- DDL hace commit implícito. Hibernate continúa con ddl-auto=validate.
-- DATETIME(6) representa UTC; la entidad usa Instant/TIMESTAMP_UTC.
CREATE TABLE checkpoint_store_catalogo.operaciones_stock (
    operacion_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    consumidor VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_contrato INT NOT NULL,
    solicitud JSON NOT NULL,
    hash_solicitud VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    estado VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resultado_aplicacion JSON NULL,
    codigo_rechazo VARCHAR(64) NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    fecha_aplicacion DATETIME(6) NULL,
    fecha_cierre DATETIME(6) NULL,
    PRIMARY KEY (operacion_id),
    INDEX idx_operacion_estado_fecha (estado, fecha_actualizacion),
    CONSTRAINT chk_operacion_uuid CHECK (
        REGEXP_LIKE(operacion_id,
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')
    ),
    CONSTRAINT chk_operacion_consumidor CHECK (consumidor = 'VENTA_SERVICE'),
    CONSTRAINT chk_operacion_version CHECK (version_contrato = 1),
    CONSTRAINT chk_operacion_hash CHECK (
        REGEXP_LIKE(hash_solicitud, '^[0-9a-f]{64}$', 'c')
    ),
    CONSTRAINT chk_operacion_estado CHECK (
        estado IN ('PENDIENTE', 'APLICADA', 'RECHAZADA', 'REVERTIDA', 'CANCELADA')
    ),
    CONSTRAINT chk_operacion_solicitud CHECK (
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
    CONSTRAINT chk_operacion_snapshot CHECK (
        resultado_aplicacion IS NULL OR JSON_SCHEMA_VALID('{
            "type":"object",
            "required":["versionContrato","items","totalProductos"],
            "additionalProperties":false,
            "properties":{
                "versionContrato":{"type":"integer","enum":[1]},
                "totalProductos":{"type":"integer","minimum":1,"maximum":9223372036854775807},
                "items":{
                    "type":"array","minItems":1,"maxItems":100,
                    "items":{
                        "type":"object",
                        "required":["productoId","sku","nombre","categoria","formato","plataforma",
                                    "cantidad","precioUnitario","subtotal"],
                        "additionalProperties":false,
                        "properties":{
                            "productoId":{"type":"integer","minimum":1,"maximum":9223372036854775807},
                            "sku":{"type":"string","minLength":1,"maxLength":64},
                            "nombre":{"type":"string","minLength":1,"maxLength":255},
                            "categoria":{"type":"string","enum":["VIDEOJUEGO","CONSOLA","ACCESORIO","FIGURA"]},
                            "formato":{"type":["string","null"],"enum":["FISICO","DIGITAL",null]},
                            "plataforma":{"type":["string","null"],"maxLength":255},
                            "cantidad":{"type":"integer","minimum":1,"maximum":2147483647},
                            "precioUnitario":{"type":"integer","minimum":1,"maximum":9223372036854775807},
                            "subtotal":{"type":"integer","minimum":1,"maximum":9223372036854775807}
                        }
                    }
                }
            }
        }', resultado_aplicacion)
    ),
    CONSTRAINT chk_operacion_resultado_estado CHECK (
        (estado IN ('APLICADA', 'REVERTIDA')
            AND resultado_aplicacion IS NOT NULL AND fecha_aplicacion IS NOT NULL)
        OR
        (estado IN ('PENDIENTE', 'RECHAZADA', 'CANCELADA')
            AND resultado_aplicacion IS NULL AND fecha_aplicacion IS NULL)
    ),
    CONSTRAINT chk_operacion_rechazo CHECK (
        (estado = 'RECHAZADA' AND codigo_rechazo IS NOT NULL
            AND CHAR_LENGTH(TRIM(codigo_rechazo)) > 0)
        OR (estado <> 'RECHAZADA' AND codigo_rechazo IS NULL)
    ),
    CONSTRAINT chk_operacion_cierre CHECK (
        (estado IN ('RECHAZADA', 'REVERTIDA', 'CANCELADA') AND fecha_cierre IS NOT NULL)
        OR (estado IN ('PENDIENTE', 'APLICADA') AND fecha_cierre IS NULL)
    ),
    CONSTRAINT chk_operacion_fechas CHECK (
        fecha_actualizacion >= fecha_creacion
        AND (fecha_aplicacion IS NULL OR
            (fecha_aplicacion >= fecha_creacion AND fecha_aplicacion <= fecha_actualizacion))
        AND (fecha_cierre IS NULL OR
            (fecha_cierre >= fecha_creacion AND fecha_cierre = fecha_actualizacion))
        AND (fecha_aplicacion IS NULL OR fecha_cierre IS NULL OR fecha_cierre >= fecha_aplicacion)
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- El servicio garantiza orden/ausencia de productos repetidos, hash canónico,
-- correspondencia solicitud/snapshot e importes. JSON_SCHEMA_VALID valida estructura.
-- No hay FK externa ni borrado automático. Conservar registros y cierres preventivos.
