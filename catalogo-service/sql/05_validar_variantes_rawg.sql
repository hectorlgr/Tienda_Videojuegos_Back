-- SOLO LECTURA. Ejecutar antes de 06, incluidos productos deshabilitados.
-- 1. SKU: debe existir uq_producto_codigo de 04. No basta una comprobación Java.
SHOW INDEX FROM `checkpoint_store_catalogo`.`productos`;

-- 2. Variantes duplicadas tras normalizar espacios/mayúsculas.
-- Debe devolver CERO filas; no se eliminan ni fusionan productos aquí.
SELECT rawg_id,
       UPPER(TRIM(REGEXP_REPLACE(plataforma, '[[:space:]]+', ' ')))
           COLLATE utf8mb4_0900_ai_ci AS plataforma_normalizada,
       formato, COUNT(*) AS cantidad, GROUP_CONCAT(id ORDER BY id) AS productos
FROM `checkpoint_store_catalogo`.`productos`
WHERE rawg_id IS NOT NULL
GROUP BY rawg_id, plataforma_normalizada, formato
HAVING COUNT(*) > 1;

-- 3. Datos incompletos o espacios no canónicos. Revisar/completar manualmente.
-- La plataforma de una variante debe ser una sola plataforma comercial.
SELECT id, codigo, rawg_id, plataforma, formato,
       UPPER(TRIM(REGEXP_REPLACE(plataforma, '[[:space:]]+', ' ')))
           AS plataforma_propuesta
FROM `checkpoint_store_catalogo`.`productos`
WHERE rawg_id IS NOT NULL AND (
    plataforma IS NULL OR CHAR_LENGTH(TRIM(plataforma)) = 0 OR formato IS NULL
    OR CAST(plataforma AS BINARY) <> CAST(TRIM(plataforma) AS BINARY)
    OR LOCATE('  ', plataforma) > 0
    OR REGEXP_LIKE(REPLACE(plataforma, ' ', ''), '[[:space:][:cntrl:]]', 'c')
);

-- 4. No debe existir un UNIQUE sobre rawg_id solo. Si existe uno heredado,
-- detenerse y revisar su retirada separadamente: este script no elimina índices.
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columnas
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'checkpoint_store_catalogo' AND TABLE_NAME = 'productos'
  AND NON_UNIQUE = 0
GROUP BY INDEX_NAME
HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'rawg_id';
