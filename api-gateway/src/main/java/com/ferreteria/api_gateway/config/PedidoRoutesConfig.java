package com.ferreteria.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PedidoRoutesConfig {
    @Bean
    public RouteLocator pedidoRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Prioridad sobre la ruta heredada YAML (orden 0), incluso en configuraciones locales.
                // Todo el namespace pertenece a Ventas; SecurityConfig permite cuatro GET y POST raíz para CLIENTE.
                .route("ventas-consulta-pedidos", route -> route.order(-10)
                        .path("/api/pedidos", "/api/pedidos/**",
                                "/api/admin/pedidos", "/api/admin/pedidos/**")
                        .uri("lb://venta-service"))
                .build();
    }
}
