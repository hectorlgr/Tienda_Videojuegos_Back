-- BASE EXISTENTE: ejecutar UNA vez, con Catálogo detenido y respaldo previo.
-- No ejecutar en una instalación creada con el script actualizado desde cero.
-- DDL MySQL hace commit implícito. No es un script de rollback transaccional.
-- Todas las columnas nuevas permiten NULL para conservar los datos y el POST actual.
ALTER TABLE `checkpoint_store_catalogo`.`productos`
    ADD COLUMN codigo VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    ADD COLUMN descripcion_corta VARCHAR(500) NULL,
    ADD COLUMN formato VARCHAR(10) NULL,
    ADD COLUMN ecosistema VARCHAR(100) NULL,
    ADD COLUMN compatibilidad VARCHAR(255) NULL,
    ADD COLUMN franquicia VARCHAR(255) NULL,
    ADD COLUMN stock_critico INT NULL,
    ADD CONSTRAINT chk_producto_codigo_no_vacio
        CHECK (codigo IS NULL OR CHAR_LENGTH(TRIM(codigo)) > 0),
    ADD CONSTRAINT chk_producto_formato
        CHECK (formato IS NULL OR CAST(formato AS BINARY) IN ('FISICO', 'DIGITAL')),
    ADD CONSTRAINT chk_producto_stock_critico
        CHECK (stock_critico IS NULL OR stock_critico >= 0);

CREATE TABLE `checkpoint_store_catalogo`.`producto_imagenes` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT NOT NULL,
    url VARCHAR(2048) NOT NULL,
    orden INT NOT NULL,
    CONSTRAINT uq_producto_imagen_orden UNIQUE (producto_id, orden),
    CONSTRAINT fk_producto_imagen_producto FOREIGN KEY (producto_id)
        REFERENCES `checkpoint_store_catalogo`.`productos` (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_producto_imagen_orden CHECK (orden BETWEEN 1 AND 4),
    CONSTRAINT chk_producto_imagen_url
        CHECK (REGEXP_LIKE(url, '^https?://[^[:space:]]+$', 'c'))
) ENGINE=InnoDB;
