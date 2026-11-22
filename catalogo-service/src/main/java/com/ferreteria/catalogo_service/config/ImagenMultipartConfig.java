package com.ferreteria.catalogo_service.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImagenMultipartConfig {
    public static final long MAX_ARCHIVO = 5L * 1024 * 1024;

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        // El margen del request permite cabeceras y boundary de multipart.
        // Temporal del contenedor; la copia persistente la gestiona ImagenService.
        return new MultipartConfigElement("", MAX_ARCHIVO, 6L * 1024 * 1024, 0);
    }
}
