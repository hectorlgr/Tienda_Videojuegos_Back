# CheckPoint Store Backend — migración estructural

Base derivada del backend de ferretería. Esta etapa retira Reseñas y Soporte;
conserva Promociones temporalmente porque Ventas aún utiliza sus cupones.
No se han realizado fusiones ni se considera el backend completamente funcional.
El detalle de dependencias y pendientes está en [MIGRACION_ESTRUCTURAL.md](MIGRACION_ESTRUCTURAL.md).

## Descripción del Proyecto

El código heredado utiliza **Spring Boot** y **Java 21** y se adaptará gradualmente a CheckPoint Store.

La estructura actual conserva catálogo, inventario, ventas, pedidos, despachos,
usuarios, autenticación y promociones heredadas. El destino acordado comprende
Identidad, Catálogo y Ventas, manteniendo provisionalmente Gateway y Eureka.

A nivel técnico, la solución implementa:

- **Patrón de Arquitectura CSR:** Clara separación de capas en `Controller` (enrutamiento REST), `Service` (lógica de negocio) y `Repository` (acceso a datos MySQL).
- **Comunicación REST:** Integración entre microservicios utilizando `WebClient` con manejo de excepciones y validación de reglas de dominio cruzadas.
- **Pruebas heredadas:** JUnit, Mockito y Jacoco; la cobertura no se ha verificado en esta etapa.
- **Seguridad y Enrutamiento:** Centralización de las peticiones a través de un **API Gateway** dinámico y descubrimiento de servicios gestionado por **Netflix Eureka**.
- **Gestión de Datos y Trazabilidad:** Inicialización de datos mixta (combinando `Liquibase` y `DataLoaders`) y centralización de logs del sistema estructurados mediante volúmenes.

---

## Integrantes

- Héctor Gutierrez
- Pablo Catalan

---

## Lista de Microservicios

El ecosistema está compuesto por los siguientes servicios:

1. **`eureka-server`** (Puerto: 8761): Servidor de descubrimiento de Spring Cloud.
2. **`api-gateway`** (Puerto: 9090): Punto único de entrada, enrutamiento y documentación centralizada.
3. **`catalogo-service`**: Gestión de información de los productos de la ferretería.
4. **`venta-service`**: Orquestación de compras, cálculos de totales y enlace con otros dominios.
5. **`usuario-service`**: Administración de perfiles de clientes.
6. **`pedido-service`**: Gestión logística de los pedidos generados.
7. **`promocion-service`**: Temporal; Ventas consulta la validación de cupones.
8. **`inventario-service`**: Descuento y adición de stock físico.
9. **`despacho-service`**: Gestión de envíos y cálculo de fletes.
10. **`identidad-service`**: Cuentas, perfiles, roles y autenticación con JWT HS256. Conserva los paquetes Java `auth_service` y las rutas externas existentes.

---

## Rutas del API Gateway

Rutas heredadas conservadas en el ejemplo del Gateway (`http://localhost:9090`).
La configuración activa aún falta; esta tabla no representa la API final de React:

| Dominio / Servicio | Ruta Expuesta en Gateway | URL Base Interna          |
| ------------------ | ------------------------ | ------------------------- |
| **Catálogo**       | `/api/productos/**`      | `lb://catalogo-service`   |
| **Ventas**         | `/api/ventas/**`         | `lb://venta-service`      |
| **Usuarios**       | `/api/usuarios/**`       | `lb://usuario-service`    |
| **Pedidos**        | `/api/pedidos/**`        | `lb://pedido-service`     |
| **Promociones**    | `/api/promociones/**`    | `lb://promocion-service`  |
| **Inventario**     | `/api/inventario/**`     | `lb://inventario-service` |
| **Despachos**      | `/api/despachos/**`      | `lb://despacho-service`   |
| **Autenticación**  | `/auth/**`               | `lb://identidad-service`  |
| **Perfil propio** | `/api/v1/usuarios/me`    | `lb://identidad-service`  |
| **Alta de personal** | `/api/v1/admin/usuarios` | `lb://identidad-service` |

---

## Documentación Interoperable (Swagger / OpenAPI)

La documentación interactiva de toda la plataforma está centralizada. No es necesario visitar la documentación de cada microservicio por separado.

- **URL de Swagger UI:** [http://localhost:9090/doc/swagger-ui.html](http://localhost:9090/doc/swagger-ui.html)

---

## Instrucciones de Ejecución

### Prerrequisitos

- **Java Development Kit (JDK):** Versión 21.
- **Maven:** Instalado en el sistema.
- **Docker y Docker Compose:** Instalados en el sistema (opción recomendada y automatizada).

### Opción 1: Despliegue Automatizado con Docker

Cada módulo conserva su `Dockerfile`. `docker-compose.yml` fue excluido de la
copia por contener una clave fija y no se reconstruye en esta etapa.
No hay un despliegue Docker preparado.

Los scripts heredados se conservan, sin ejecutarlos. `build-all.bat` contiene
eliminación global de contenedores e imágenes; no debe utilizarse como procedimiento
de validación de esta migración.

### Opción 2: Ejecución Manual Local

El script `iniciar_servicios.bat` refleja los módulos conservados, pero no se ha
ejecutado. Antes de iniciar servicios deben prepararse las configuraciones activas
de Auth y Gateway y conexiones a bases propias. Véase [COPIA_BASE.md](COPIA_BASE.md).

### Pruebas Unitarias y Cobertura (Jacoco)

Para verificar la estabilidad de las reglas de negocio y asegurar el cumplimiento de la cobertura obligatoria (superior o igual al 80%), puedes ejecutar el ciclo de pruebas de Maven en cualquiera de los módulos:

```bash
mvnw clean test

```

Los reportes de cobertura interactivos de Jacoco se generarán automáticamente y se pueden consultar abriendo el archivo `target/site/jacoco/index.html` en el navegador web de tu preferencia.
