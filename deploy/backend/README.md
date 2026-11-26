# Backend en la red Docker

Montar este directorio en `/config` en solo lectura. En Compose, declarar
`SPRING_CONFIG_LOCATION` en el entorno de cada servicio con el valor completo
de la tabla. Spring lee las ubicaciones de izquierda a derecha. La extensión
entre corchetes indica cómo interpretar la plantilla empaquetada. Las últimas
ubicaciones sustituyen únicamente las propiedades de despliegue.

| Servicio DNS (`DOCKER_SERVICE_NAME`) | Puerto | `SPRING_CONFIG_LOCATION` |
| --- | ---: | --- |
| eureka | 8761 | `classpath:/application.properties,file:/config/network.properties` |
| identidad-service | 9096 | `classpath:/application.properties.example[.properties],file:/config/network.properties,file:/config/database.properties` |
| catalogo-service | 9091 | `classpath:/application.properties.example[.properties],file:/config/network.properties,file:/config/database.properties` |
| venta-service | 9094 | `classpath:/application.properties.example[.properties],file:/config/network.properties,file:/config/database.properties` |
| api-gateway | 9090 | `classpath:/application.yml.example[.yaml],file:/config/network.properties,file:/config/gateway.properties` |

Asignar a cada contenedor el nombre DNS indicado y definir `DOCKER_SERVICE_NAME`
con ese mismo valor. Conectar los cinco módulos y `mysql` a la misma red sin
asignar `ports`. El descubrimiento usa `http://eureka:8761/eureka/`. Cada cliente
anuncia su nombre DNS y su puerto interno. Iniciar los servicios de datos
cuando MySQL acepte conexiones después de la inicialización aprobada.

| Servicio | `MYSQL_SCHEMA` | `MYSQL_APP_USERNAME` | Fuente de `MYSQL_APP_PASSWORD` |
| --- | --- | --- | --- |
| identidad-service | checkpoint_store_identidad | checkpoint_app | clave de checkpoint_app |
| catalogo-service | checkpoint_store_catalogo | checkpoint_app | clave de checkpoint_app |
| venta-service | checkpoint_store_ventas | checkpoint_ventas_app | clave de checkpoint_ventas_app |

Inyectar las credenciales de las cuentas creadas por el inicializador MySQL.
JDBC exige transporte TLS. Las tres plantillas conservan
`spring.jpa.hibernate.ddl-auto=validate` y `spring.sql.init.mode=never`.

- **Identidad, Catálogo, Ventas y Gateway.** Inyectar el mismo `JWT_SECRET`
  aleatorio de al menos 32 bytes UTF-8 para los tokens de usuario.
- **Catálogo y Ventas.** Inyectar `STOCK_SERVICE_JWT_SECRET` con una segunda
  clave aleatoria de al menos 32 bytes UTF-8 compartida solo por ambos.
- **Ventas.** Definir `CHECKOUT_VERSION_CONDICIONES=CHECKPOINT_COMPRA_V1` para
  habilitar las preparaciones conforme a las condiciones aprobadas.
- **Catálogo.** Definir `CATALOGO_IMAGENES_DIR` con la ruta de un volumen
  escribible, por ejemplo `/var/lib/checkpoint/imagenes`, y
  `CATALOGO_PUBLIC_BASE_URL` con la URL pública que sirve las imágenes.
  `RAWG_API_KEY` es opcional para la integración RAWG.
- **Gateway.** Definir `CORS_PUBLIC_ORIGIN` con el origen exacto del frontend,
  por ejemplo `https://tienda.example.com`, sin ruta ni barra final.
- **Identidad.** En el arranque normal omitir el perfil `bootstrap-admin`,
  su habilitación y sus credenciales.

Suministrar los secretos desde el entorno de despliegue al crear los
contenedores, con acceso limitado por servicio. Mantener sus valores fuera
del repositorio, de los argumentos de construcción y de los informes.
Los archivos de este directorio se montan en ejecución y las plantillas
versionadas ya están permitidas por los `.dockerignore` de cada módulo.
