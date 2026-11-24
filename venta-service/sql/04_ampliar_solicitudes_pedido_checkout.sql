-- Etapa 4.6B.1. MANUAL, MySQL 8.4; SQL 03 debe estar aplicado.
-- Detener escritores y revisar SHOW CREATE TABLE antes de aplicar una sola vez.
-- Si existen filas RESUELTA sin resultado historico recuperable, DETENERSE:
-- este ALTER fallara en vez de inventar resultados. Ver ARRANQUE_LOCAL.md.
-- DDL con commit implicito. No ejecutar de nuevo si ya se aplico.
-- Solo solicitudes_pedido; sin DML, GRANT ni cambios en SQL 01/02/03.

ALTER TABLE checkpoint_store_ventas.solicitudes_pedido
    ADD COLUMN checkout JSON NULL,
    ADD COLUMN hash_checkout VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN codigo_compensacion VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN resultado JSON NULL,
    DROP CHECK chk_solicitud_estado,
    ADD CONSTRAINT chk_solicitud_estado CHECK (
        estado IN ('PENDIENTE', 'RECONCILIACION_PENDIENTE', 'COMPENSACION_PENDIENTE', 'RESUELTA')
    ),
    ADD CONSTRAINT chk_solicitud_checkout_par CHECK (
        (checkout IS NULL AND hash_checkout IS NULL)
        OR (checkout IS NOT NULL AND hash_checkout IS NOT NULL)
    ),
    ADD CONSTRAINT chk_solicitud_hash_checkout CHECK (
        hash_checkout IS NULL OR REGEXP_LIKE(hash_checkout, '^[0-9a-f]{64}$', 'c')
    ),
    ADD CONSTRAINT chk_solicitud_checkout_json CHECK (
        checkout IS NULL OR JSON_SCHEMA_VALID('{
            "type":"object",
            "required":["versionContrato","entrada","contextoServidor"],
            "additionalProperties":false,
            "properties":{
                "versionContrato":{"type":"integer","enum":[1]},
                "entrada":{
                    "type":"object",
                    "required":["entrega","limiteTotalClp","aceptacion"],
                    "additionalProperties":false,
                    "properties":{
                        "entrega":{
                            "type":["object","null"],
                            "required":["destinatario","telefono","direccion","region","comuna","referencia"],
                            "additionalProperties":false,
                            "properties":{
                                "destinatario":{"type":"string","minLength":1,"maxLength":255},
                                "telefono":{"type":"string","minLength":1,"maxLength":30},
                                "direccion":{"type":"string","minLength":1,"maxLength":255},
                                "region":{"type":"string","minLength":1,"maxLength":100},
                                "comuna":{"type":"string","minLength":1,"maxLength":100},
                                "referencia":{"type":["string","null"],"minLength":1,"maxLength":500}
                            }
                        },
                        "limiteTotalClp":{"type":"integer","minimum":1,"maximum":9223372036854775807},
                        "aceptacion":{
                            "type":"object",
                            "required":["versionCondiciones","aceptaPrecioVigenteHastaLimite"],
                            "additionalProperties":false,
                            "properties":{
                                "versionCondiciones":{"type":"string","minLength":1,"maxLength":64},
                                "aceptaPrecioVigenteHastaLimite":{"type":"boolean","enum":[true]}
                            }
                        }
                    }
                },
                "contextoServidor":{
                    "type":"object",
                    "required":["politicaDespacho","versionPoliticaDespacho","costoDespacho","versionContratoComercial"],
                    "additionalProperties":false,
                    "properties":{
                        "politicaDespacho":{"type":"string","minLength":1,"maxLength":64},
                        "versionPoliticaDespacho":{"type":"integer","minimum":1,"maximum":2147483647},
                        "costoDespacho":{"type":"integer","minimum":0,"maximum":9223372036854775807},
                        "versionContratoComercial":{"type":"integer","enum":[1]}
                    }
                }
            }
        }', checkout)
    ),
    ADD CONSTRAINT chk_solicitud_codigo_compensacion CHECK (
        codigo_compensacion IS NULL
        OR REGEXP_LIKE(codigo_compensacion, '^[A-Z][A-Z0-9_]{0,63}$', 'c')
    ),
    ADD CONSTRAINT chk_solicitud_compensacion_estado CHECK (
        (estado = 'COMPENSACION_PENDIENTE' AND codigo_compensacion IS NOT NULL AND resultado IS NULL)
        OR (estado IN ('PENDIENTE', 'RECONCILIACION_PENDIENTE') AND codigo_compensacion IS NULL)
        OR estado = 'RESUELTA'
    ),
    ADD CONSTRAINT chk_solicitud_resultado_estado CHECK (
        (estado = 'RESUELTA' AND resultado IS NOT NULL
            AND token_reclamacion IS NULL AND bloqueo_hasta IS NULL)
        OR (estado <> 'RESUELTA' AND resultado IS NULL)
    ),
    ADD CONSTRAINT chk_solicitud_resultado_json CHECK (
        resultado IS NULL OR JSON_SCHEMA_VALID('{
            "type":"object",
            "required":["versionContrato","tipo","codigo","estadoRemoto","pedidoId"],
            "additionalProperties":false,
            "properties":{
                "versionContrato":{"type":"integer","enum":[1]},
                "tipo":{"type":"string"},
                "codigo":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{0,63}$","minLength":1,"maxLength":64},
                "estadoRemoto":{"type":["string","null"]},
                "pedidoId":{"type":["integer","null"],"minimum":1,"maximum":9223372036854775807}
            },
            "oneOf":[
                {"properties":{"tipo":{"enum":["PEDIDO_CREADO"]},"estadoRemoto":{"enum":["APLICADA"]},"pedidoId":{"type":"integer"}}},
                {"properties":{"tipo":{"enum":["RECHAZO_DEFINITIVO"]},"estadoRemoto":{"enum":["RECHAZADA"]},"pedidoId":{"type":"null"}}},
                {"properties":{"tipo":{"enum":["COMPENSACION_CONFIRMADA"]},"estadoRemoto":{"enum":["REVERTIDA"]},"pedidoId":{"type":"null"}}},
                {"properties":{"tipo":{"enum":["CIERRE_PREVENTIVO_CONFIRMADO"]},"estadoRemoto":{"enum":["CANCELADA"]},"pedidoId":{"type":"null"}}},
                {"properties":{"tipo":{"enum":["RECHAZO_LOCAL"]},"estadoRemoto":{"type":"null"},"pedidoId":{"type":"null"}}}
            ]
        }', resultado)
    ),
    ADD CONSTRAINT chk_solicitud_resultado_compensacion CHECK (
        resultado IS NULL OR (
            (JSON_UNQUOTE(JSON_EXTRACT(resultado, '$.tipo')) IN
                ('COMPENSACION_CONFIRMADA', 'CIERRE_PREVENTIVO_CONFIRMADO')
                AND codigo_compensacion IS NOT NULL)
            OR (JSON_UNQUOTE(JSON_EXTRACT(resultado, '$.tipo')) IN
                ('PEDIDO_CREADO', 'RECHAZO_DEFINITIVO', 'RECHAZO_LOCAL')
                AND codigo_compensacion IS NULL)
        )
    ),
    ADD CONSTRAINT chk_solicitud_pedido_checkout CHECK (
        resultado IS NULL
        OR JSON_UNQUOTE(JSON_EXTRACT(resultado, '$.tipo')) <> 'PEDIDO_CREADO'
        OR (checkout IS NOT NULL AND hash_checkout IS NOT NULL)
    );

-- Se conservan PK, UNIQUE, indices, CHECK del JSON de stock, leases y fechas.
-- No se rellenan checkouts/resultados antiguos ni se cambian hashes existentes.
-- CHECK valida la fila actual: no demuestra irrevocabilidad historica, existencia
-- del pedido ni confirmacion remota. El futuro coordinador debera comprobarlo.
