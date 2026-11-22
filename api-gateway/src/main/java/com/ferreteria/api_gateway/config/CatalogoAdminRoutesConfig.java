package com.ferreteria.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class CatalogoAdminRoutesConfig {
    // Se suma a las rutas existentes sin depender del YAML local o de su ejemplo.
    @Bean
    public RouteLocator catalogoAdminRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("catalogo-admin-productos", route -> route
                        .path("/api/admin/productos", "/api/admin/productos/{id}")
                        .and().method(HttpMethod.GET)
                        .uri("lb://catalogo-service"))
                .build();
    }
}
