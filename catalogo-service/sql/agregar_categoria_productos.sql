-- Solo para la tabla existente VACIA creada antes de incorporar categoria.
-- Ejecutar una sola vez con una cuenta administrativa en MySQL Workbench.
-- No volver a ejecutar el script de creacion inicial.
ALTER TABLE `checkpoint_store_catalogo`.`productos`
    ADD COLUMN categoria VARCHAR(20) NOT NULL AFTER nombre,
    ADD CONSTRAINT chk_producto_categoria CHECK (
        CAST(categoria AS BINARY) IN ('VIDEOJUEGO', 'CONSOLA', 'ACCESORIO', 'FIGURA')
    );
