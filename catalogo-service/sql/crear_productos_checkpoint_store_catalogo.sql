-- Ejecutar una sola vez en MySQL 8.4, después de crear la base vacía.
-- El nombre cualificado evita depender del esquema seleccionado en Workbench.
-- Si la tabla ya existe, CREATE TABLE falla sin reemplazarla.
-- Esquema TRANSITORIO compatible con los DTO actuales. Tras adaptar el formulario
-- y completar datos, ejecutar 03 y 04 para exigir SKU y campos por categoría.
CREATE TABLE `checkpoint_store_catalogo`.`productos` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    categoria VARCHAR(20) NOT NULL,
    descripcion TEXT,
    marca VARCHAR(100),
    plataforma VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
    genero VARCHAR(255),
    imagen_url VARCHAR(2048),
    precio INT NOT NULL,
    precio_oferta INT,
    en_oferta BOOLEAN NOT NULL DEFAULT FALSE,
    stock INT NOT NULL DEFAULT 0,
    habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    rawg_id BIGINT,
    codigo VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    descripcion_corta VARCHAR(500),
    formato VARCHAR(10),
    ecosistema VARCHAR(100),
    compatibilidad VARCHAR(255),
    franquicia VARCHAR(255),
    stock_critico INT,
    CONSTRAINT uq_producto_variante_rawg UNIQUE (rawg_id, plataforma, formato),
    CONSTRAINT chk_producto_variante_rawg CHECK (
        rawg_id IS NULL OR (
            plataforma IS NOT NULL AND CHAR_LENGTH(TRIM(plataforma)) > 0
            AND formato IS NOT NULL
            AND CAST(plataforma AS BINARY) = CAST(TRIM(plataforma) AS BINARY)
            AND LOCATE('  ', plataforma) = 0
            AND NOT REGEXP_LIKE(REPLACE(plataforma, ' ', ''), '[[:space:][:cntrl:]]', 'c')
        )
    ),
    CONSTRAINT chk_producto_codigo_no_vacio
        CHECK (codigo IS NULL OR CHAR_LENGTH(TRIM(codigo)) > 0),
    CONSTRAINT chk_producto_formato
        CHECK (formato IS NULL OR CAST(formato AS BINARY) IN ('FISICO', 'DIGITAL')),
    CONSTRAINT chk_producto_stock_critico CHECK (stock_critico IS NULL OR stock_critico >= 0),
    CONSTRAINT chk_producto_nombre CHECK (CHAR_LENGTH(TRIM(nombre)) > 0),
    CONSTRAINT chk_producto_categoria CHECK (
        CAST(categoria AS BINARY) IN ('VIDEOJUEGO', 'CONSOLA', 'ACCESORIO', 'FIGURA')
    ),
    CONSTRAINT chk_producto_precio CHECK (precio > 0),
    CONSTRAINT chk_producto_precio_oferta CHECK (precio_oferta IS NULL OR precio_oferta > 0),
    CONSTRAINT chk_producto_oferta CHECK (
        en_oferta = FALSE OR (precio_oferta IS NOT NULL AND precio_oferta < precio)
    ),
    CONSTRAINT chk_producto_stock CHECK (stock >= 0),
    CONSTRAINT chk_producto_en_oferta CHECK (en_oferta IN (0, 1)),
    CONSTRAINT chk_producto_habilitado CHECK (habilitado IN (0, 1))
) ENGINE=InnoDB;

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
