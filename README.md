# CheckPoint Store · Backend

CheckPoint Store es una tienda académica de videojuegos, consolas, accesorios y figuras. El backend permite al equipo administrar el catálogo y las existencias, y al cliente registrarse, comprar y consultar sus pedidos. El frontend se mantiene en un repositorio independiente.

## Arquitectura y puertos

El frontend envía sus solicitudes al Gateway. Eureka permite al Gateway localizar los servicios registrados y a Ventas localizar Catálogo durante el checkout.

| Módulo              | Puerto local | Responsabilidad                                                     | Spring Boot | Spring Cloud | Maven Wrapper¹ |
| ------------------- | -----------: | ------------------------------------------------------------------- | ----------- | ------------ | -------------- |
| `eureka-server`     |         8761 | Registro y descubrimiento de instancias                             | 4.0.6       | 2025.1.1     | 3.9.16         |
| `identidad-service` |         9096 | Registro, login, perfil y roles de acceso                           | 4.0.5       | 2025.1.1     | 3.9.15         |
| `catalogo-service`  |         9091 | Productos, imágenes, ofertas y operaciones de stock                 | 4.0.5       | 2025.1.1     | 3.9.14         |
| `venta-service`     |         9094 | Checkout, consultas de pedidos y lecturas administrativas de ventas | 4.0.5       | 2025.1.1     | 3.9.14         |
| `api-gateway`       |         9090 | Entrada HTTP, autorización, CORS y documentación OpenAPI            | 3.3.5       | 2023.0.3     | 3.9.14         |

¹ Versión de Maven que descarga el wrapper de cada módulo. Los cinco utilizan Java 21. Los servicios de negocio persisten sus datos en MySQL 8.4 (puerto local 3306). Spring Security valida los JWT de acceso; las operaciones internas de stock entre Ventas y Catálogo utilizan un JWT de servicio con un secreto distinto.

## Funcionalidades principales

- **Identidad.** El cliente puede crear su cuenta, iniciar sesión y mantener su perfil. El alta de personal requiere un administrador.
- **Catálogo.** El público consulta los productos habilitados. El administrador gestiona fichas, precios en CLP, stock e imágenes. RAWG es un asistente opcional para crear videojuegos: permite buscar un título y aprovechar sus metadatos; el administrador completa los datos comerciales. La integración conserva el idioma de origen y no realiza traducción automática.
- **Ventas y pedidos.** El checkout autenticado (`POST /api/pedidos`) obtiene el comprador del JWT, valida las condiciones de compra y coordina el stock con Catálogo. Una clave de idempotencia permite reintentar la misma solicitud. El cliente consulta sus pedidos y el administrador dispone de consultas globales de pedidos y ventas.

## Preparación y arranque local

1. Preparar Windows con Git, JDK 21, Windows PowerShell 5.1 y MySQL Server 8.4. El primer uso de los wrappers requiere acceso a las descargas de Maven o una caché previamente preparada.
2. Crear los tres esquemas y las cuentas de aplicación, aplicar los scripts manuales correspondientes y preparar el primer administrador según las guías. Hibernate valida el esquema al arrancar. [Instalación desde cero](docs/instalacion/INSTALACION_LOCAL_DESDE_CERO.md) · [Esquema y permisos de Ventas](docs/ventas/ARRANQUE_LOCAL.md)
3. Con MySQL iniciado, abrir [iniciar_checkpoint_local.bat](iniciar_checkpoint_local.bat). Introducir las contraseñas de `checkpoint_app` y `checkpoint_ventas_app` en sus respectivos campos ocultos. La clave RAWG es opcional. El lanzador inicia los servicios en el orden de la tabla y comprueba su disponibilidad. [Guía de arranque y detención](docs/instalacion/ARRANQUE_CHECKPOINT_LOCAL.md)
4. Esperar el mensaje **Entorno disponible**. Abrir [Swagger UI del Gateway](http://localhost:9090/doc/swagger-ui.html) para explorar Identidad, Catálogo y Ventas. Las operaciones de pedidos aparecen en Ventas. Tras reiniciar el entorno, volver a iniciar sesión para obtener un token válido.

## Índice técnico

| Área        | Documentación                                                                                                                                                                                     |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Instalación | [Requisitos y preparación](docs/instalacion/INSTALACION_LOCAL_DESDE_CERO.md) · [Arranque integrado](docs/instalacion/ARRANQUE_CHECKPOINT_LOCAL.md)                                                |
| Gateway     | [CORS para el frontend local](docs/gateway/CORS_FRONTEND_LOCAL.md)                                                                                                                                |
| Identidad   | [Roles y permisos](docs/identidad/ROLES_IDENTIDAD.md) · [Primer administrador](docs/identidad/INICIALIZAR_ADMIN_LOCAL.md)                                                                         |
| Catálogo    | [Contrato con el frontend](docs/catalogo/CONTRATO_FRONTEND_CATALOGO.md) · [Importación con RAWG](docs/catalogo/IMPORTACION_RAWG_POSTMAN.md)                                                       |
| Ventas      | [Contrato de checkout](docs/ventas/CHECKOUT_HTTP.md) · [Consultas de pedidos](docs/ventas/CONSULTAS_PEDIDOS.md) · [Coordinación, recuperación y compensación](docs/ventas/COORDINADOR_PEDIDOS.md) |

## Alcance del MVP académico

El entorno documentado se ejecuta en Windows con MySQL e imágenes almacenadas localmente. Un pedido registrado no acredita pago ni despacho. Si el checkout devuelve HTTP 202, la solicitud sigue pendiente; el cliente debe seguir el procedimiento de reintento del contrato de checkout. La aceptación en un equipo limpio y los casos de integración y concurrencia señalados como pendientes en las guías requieren validación en ejecución.

## Integrantes

Vicente Briones
Héctor Gutierrez
Luisana Rivero
