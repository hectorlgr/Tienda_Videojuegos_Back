-- SOLO LECTURA. Ejecutar tras 01; no asigna ni inventa datos comerciales.
-- Propuesta: prefijo de categoría + ID local, con un mínimo de 3 dígitos.
-- Se usa GREATEST para que LPAD NO trunque IDs mayores que 999.
-- Un SKU ya asignado se conserva incluso si cambia la categoría.
SELECT id, categoria, nombre, codigo AS codigo_actual,
       CONCAT(CASE categoria
                  WHEN 'VIDEOJUEGO' THEN 'JUE'
                  WHEN 'CONSOLA' THEN 'CON'
                  WHEN 'ACCESORIO' THEN 'ACC'
                  WHEN 'FIGURA' THEN 'FIG'
              END, '-',
              LPAD(CAST(id AS CHAR), GREATEST(3, CHAR_LENGTH(CAST(id AS CHAR))), '0'))
       AS codigo_propuesto
FROM `checkpoint_store_catalogo`.`productos`
WHERE codigo IS NULL OR CHAR_LENGTH(TRIM(codigo)) = 0
ORDER BY id;

-- Revisar/exportar el resultado antes de asignar códigos. Plantilla MANUAL:
-- UPDATE checkpoint_store_catalogo.productos
-- SET codigo = 'JUE-001'
-- WHERE id = 1 AND codigo IS NULL;
--
-- No se incluye UPDATE masivo: un código propuesto podría existir en otra fila.
-- Tras revisar colisiones, ejecutar solo asignaciones aprobadas en una transacción.
-- No adivinar formato, ecosistema, compatibilidad ni franquicia a partir de categoría.
-- Completar esos valores manualmente con datos conocidos, incluidos productos ocultos.
