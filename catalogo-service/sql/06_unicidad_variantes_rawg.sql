-- BASE EXISTENTE: ejecutar UNA vez, después de revisar 05 sin incidencias.
-- Requisito previo: uq_producto_codigo instalado mediante 04.
-- Detener escritores, respaldar y revisar SHOW CREATE TABLE antes del DDL.
-- No se actualizan filas ni se eliminan datos. DDL con commit implícito.
ALTER TABLE `checkpoint_store_catalogo`.`productos`
    MODIFY COLUMN plataforma VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    ADD CONSTRAINT chk_producto_variante_rawg CHECK (
        rawg_id IS NULL OR (
            plataforma IS NOT NULL AND CHAR_LENGTH(TRIM(plataforma)) > 0
            AND formato IS NOT NULL
            AND CAST(plataforma AS BINARY) = CAST(TRIM(plataforma) AS BINARY)
            AND LOCATE('  ', plataforma) = 0
            AND NOT REGEXP_LIKE(REPLACE(plataforma, ' ', ''), '[[:space:][:cntrl:]]', 'c')
        )
    ),
    ADD CONSTRAINT uq_producto_variante_rawg UNIQUE (rawg_id, plataforma, formato);

-- MySQL permite múltiples NULL: los productos manuales (rawg_id NULL)
-- no compiten por esta clave. Los importados requieren plataforma/formato no NULL.
-- La collation evita duplicados por mayúsculas/acentos; el CHECK exige espacios
-- simples sin bordes para que los espacios repetidos no eludan la unicidad.
