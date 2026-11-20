-- Ejecutar una sola vez en MySQL 8.4, después de crear la base vacía.
-- El nombre cualificado evita depender del esquema seleccionado en Workbench.
-- Si la tabla ya existe, CREATE TABLE falla sin reemplazarla.
CREATE TABLE `checkpoint_store_catalogo`.`productos` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    categoria VARCHAR(20) NOT NULL,
    descripcion TEXT,
    marca VARCHAR(100),
    plataforma VARCHAR(255),
    genero VARCHAR(255),
    imagen_url VARCHAR(2048),
    precio INT NOT NULL,
    precio_oferta INT,
    en_oferta BOOLEAN NOT NULL DEFAULT FALSE,
    stock INT NOT NULL DEFAULT 0,
    habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    rawg_id BIGINT,
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
);
