-- FASE FUTURA: NO aplicar con los DTO/endpoints actuales.
-- Requisitos: adaptar POST/PUT al nuevo formulario, completar datos existentes,
-- ejecutar 03 sin incidencias y detener escritores mientras se aplica este DDL.
-- Un solo ALTER: MySQL 8.4/InnoDB valida los registros existentes; si hay valores
-- nulos, duplicados o incompletos, falla sin aplicar parcialmente este ALTER.
-- Este DDL hace commit implícito; no usar ROLLBACK como mecanismo de reversión.
ALTER TABLE `checkpoint_store_catalogo`.`productos`
    MODIFY COLUMN codigo VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    ADD CONSTRAINT uq_producto_codigo UNIQUE (codigo),
    ADD CONSTRAINT chk_producto_campos_categoria CHECK (
        (CAST(categoria AS BINARY) <> 'VIDEOJUEGO' OR formato IS NOT NULL)
        AND (CAST(categoria AS BINARY) <> 'CONSOLA'
             OR (ecosistema IS NOT NULL AND CHAR_LENGTH(TRIM(ecosistema)) > 0))
        AND (CAST(categoria AS BINARY) <> 'ACCESORIO'
             OR (compatibilidad IS NOT NULL AND CHAR_LENGTH(TRIM(compatibilidad)) > 0))
        AND (CAST(categoria AS BINARY) <> 'FIGURA'
             OR (franquicia IS NOT NULL AND CHAR_LENGTH(TRIM(franquicia)) > 0))
    );
