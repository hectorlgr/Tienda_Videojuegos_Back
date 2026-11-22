package com.ferreteria.catalogo_service.exception;

public class ImagenStorageException extends RuntimeException {
    public ImagenStorageException() {
        // No conservar excepciones de filesystem ni sus rutas en la respuesta.
        super("No se pudo acceder al almacenamiento de imágenes.");
    }
}
