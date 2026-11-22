-- SOLO LECTURA. Todas las consultas de incidencias deben devolver CERO filas
-- antes de aplicar 04. Incluye productos deshabilitados.
SELECT id, categoria, codigo
FROM `checkpoint_store_catalogo`.`productos`
WHERE codigo IS NULL OR CHAR_LENGTH(TRIM(codigo)) = 0;

-- Misma collation que el futuro UNIQUE: no distingue mayúsculas ni acentos.
SELECT codigo, COUNT(*) AS repeticiones, GROUP_CONCAT(id ORDER BY id) AS productos
FROM `checkpoint_store_catalogo`.`productos`
WHERE codigo IS NOT NULL
GROUP BY codigo
HAVING COUNT(*) > 1;

SELECT id, categoria, formato, ecosistema, compatibilidad, franquicia
FROM `checkpoint_store_catalogo`.`productos`
WHERE (categoria = 'VIDEOJUEGO' AND formato IS NULL)
   OR (categoria = 'CONSOLA' AND (ecosistema IS NULL OR CHAR_LENGTH(TRIM(ecosistema)) = 0))
   OR (categoria = 'ACCESORIO' AND (compatibilidad IS NULL OR CHAR_LENGTH(TRIM(compatibilidad)) = 0))
   OR (categoria = 'FIGURA' AND (franquicia IS NULL OR CHAR_LENGTH(TRIM(franquicia)) = 0));

SELECT id, stock_critico, formato
FROM `checkpoint_store_catalogo`.`productos`
WHERE stock_critico < 0
   OR (formato IS NOT NULL AND CAST(formato AS BINARY) NOT IN ('FISICO', 'DIGITAL'));

-- Debe estar vacío para admitir variantes. No se elimina ningún índice aquí.
SELECT INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'checkpoint_store_catalogo' AND TABLE_NAME = 'productos'
  AND COLUMN_NAME = 'rawg_id' AND NON_UNIQUE = 0;
