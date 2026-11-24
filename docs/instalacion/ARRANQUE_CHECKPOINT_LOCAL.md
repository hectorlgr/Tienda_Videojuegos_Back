# Arranque local de CheckPoint Store

## Doble clic

1. Tener Java 21 disponible y MySQL 8.4 iniciado en 127.0.0.1:3306.
   Las bases `checkpoint_store_identidad`, `checkpoint_store_catalogo` y `checkpoint_store_ventas`
   deben existir con sus esquemas previamente preparados. La cuenta existente
   `checkpoint_app` debe tener permisos de operacion sobre Identidad y Catalogo.
   Ventas usa `checkpoint_ventas_app`. Consultar los permisos mínimos para [registrar solicitudes](../ventas/ARRANQUE_LOCAL.md) y [materializar pedidos](../ventas/MATERIALIZACION_PEDIDO.md).
   El esquema de Catalogo incluye operaciones_stock (SQL 07 ya aplicado).
2. Detener cualquier entorno anterior que ocupe 8761, 9096, 9091, 9094 o 9090.
3. Hacer doble clic en `iniciar_checkpoint_local.bat`, en la raiz del backend.
4. Escribir una sola vez la contrasena MySQL de checkpoint_app cuando se
   solicite (entrada oculta), y una vez la de checkpoint_ventas_app.
   Pueden ser distintas; no se reutiliza implicitamente una cuenta para la otra.
   RAWG se solicita de forma opcional y oculta, salvo que ya venga del entorno.
   No se solicita ni muestra ningun secreto JWT.
5. Esperar el mensaje `Entorno disponible`. Se abren cinco ventanas:
   Eureka, Identidad, Catalogo, Ventas y Gateway.
6. Volver a iniciar sesion en React/Postman: cada ejecucion genera una nueva clave
   compartida y los tokens del entorno anterior ya no sirven.

`iniciar_checkpoint_local.bat` es el acceso principal. Invoca `iniciar_identidad.ps1 -IncluirCatalogo` para iniciar los cinco servicios.

## Configuracion seleccionada y variables

El modo de cinco servicios selecciona explicitamente archivos existentes
mediante SPRING_CONFIG_LOCATION heredado por cada proceso:

| Servicio | Puerto | Configuracion |
| --- | --- | --- |
| Eureka | 8761 | eureka-server/src/main/resources/application.properties |
| Identidad | 9096 | identidad-service/src/main/resources/application.properties.example |
| Catalogo | 9091 | catalogo-service/src/main/resources/application.properties.example |
| Ventas | 9094 | venta-service/src/main/resources/application.properties.example |
| Gateway | 9090 | api-gateway/src/main/resources/application.yml.example |

El sufijo `[.properties]` o `[.yaml]` indica el formato de los ejemplos a Spring.
No se copian ni sobrescriben configuraciones privadas. En particular,
Catalogo no carga el application.properties heredado que apuntaba a db_catalogo.
Los ejemplos no son secretos y no se modifican durante el arranque.
El modo nuevo fija los puertos existentes y escucha en loopback (127.0.0.1).

- Identidad recibe DB_URL=jdbc:mysql://127.0.0.1:3306/checkpoint_store_identidad,
  DB_USERNAME=checkpoint_app y DB_PASSWORD.
- Catalogo recibe CATALOGO_DB_USERNAME=checkpoint_app y CATALOGO_DB_PASSWORD
   con la misma contrasena. Su ejemplo fija exclusivamente checkpoint_store_catalogo.
- Ventas recibe VENTAS_DB_USERNAME=checkpoint_ventas_app y VENTAS_DB_PASSWORD
  con su propia contrasena. Su ejemplo fija checkpoint_store_ventas, JDBC UTC
  y ddl-auto=validate. No recibe las credenciales de Identidad/Catalogo.
- JWT_SECRET se genera una sola vez con 64 bytes aleatorios criptograficos
   convertidos a texto Base64; Identidad, Catalogo, Ventas y Gateway heredan ese mismo
  texto. Los servicios usan sus bytes UTF-8, sin decodificarlo como Base64.
- Eureka se inicia antes de establecer credenciales o JWT_SECRET.
- STOCK_SERVICE_JWT_SECRET se genera con otros 64 bytes aleatorios, o se reutiliza
  una variable heredada valida (no vacia/blanca, minimo 32 bytes UTF-8).
  Una heredada invalida provoca un error antes de iniciar Eureka.
  Se comprueba que ambos secretos sean diferentes, respetando mayusculas.
  Su valor permanece solo en memoria hasta iniciar Catalogo. Catalogo y Ventas
  heredan exactamente el mismo texto; Identidad, Eureka y Gateway no lo reciben.
  Ventas firma el JWT de servicio para las llamadas de stock a Catálogo. Solo Ventas recibe `CHECKOUT_VERSION_CONDICIONES=CHECKPOINT_COMPRA_V1`.
- Gateway se inicia despues de retirar las credenciales MySQL del entorno
   del lanzador, junto con el secreto de stock y RAWG.
   Los hijos ya iniciados conservan su propia copia.
- BOOTSTRAP_ADMIN_ENABLED=false; ddl-auto=validate; sql.init.mode=never;
  Liquibase y Flyway deshabilitados. No se crean usuarios, tablas ni datos.
- El modo nuevo neutraliza overrides habituales de configuracion/perfiles
  y restaura las variables del proceso lanzador al salir, tambien ante fallos.

Las credenciales y la clave solo se transmiten por entorno heredado, nunca
mediante archivos, argumentos Maven, mensajes ni logs del lanzador. No activar
trazas de entorno o volcados de configuracion al investigar errores.
El usuario local del sistema operativo sigue pudiendo inspeccionar sus procesos.
No se escriben secretos en temporales ni se usa setx. La restauracion final solo
afecta al proceso lanzador, no a variables permanentes de Windows.
Una variable definida en otra terminal no llega al Explorador: para reutilizar
una existente, el BAT debe iniciarse desde el proceso que la contiene.

| Proceso | Credenciales MySQL | JWT humano | JWT stock | RAWG |
| --- | --- | --- | --- | --- |
| Eureka | Ninguna | No | No | No |
| Identidad | checkpoint_app / DB_* | Si | No | No |
| Catalogo | checkpoint_app / CATALOGO_DB_* | Si | Si | Opcional |
| Ventas | checkpoint_ventas_app / VENTAS_DB_* | Si | Si | No |
| Gateway | Ninguna | Si | No | No |

Catalogo conserva CATALOGO_IMAGENES_DIR externo o, por defecto,
`%LOCALAPPDATA%\CheckPointStore\imagenes`; CATALOGO_PUBLIC_BASE_URL conserva
el valor heredado o `http://localhost:9090`. Estas variables no pasan a Ventas.

## Orden y comprobaciones

1. Comprobar wrappers, archivos de configuracion y puertos libres (incluidos
   listeners IPv6). Comprobar disponibilidad TCP de MySQL, sin ejecutar SQL.
2. Iniciar Eureka y esperar HTTP 200 en 8761.
3. Iniciar Identidad, esperar su OpenAPI y registro IDENTIDAD-SERVICE en estado UP.
4. Iniciar Catalogo, esperar su OpenAPI y registro CATALOGO-SERVICE en estado UP.
5. Iniciar Ventas, esperar `/api/ventas/v3/api-docs` en 9094 y registro
   VENTA-SERVICE en estado UP.
6. Iniciar Gateway, esperar su ruta OpenAPI hacia Identidad, registro
   API-GATEWAY en estado UP y rutas OpenAPI hacia Catalogo y Ventas.

Cada espera tiene un limite de 180 segundos. Si hay un fallo, el lanzador
informa la etapa y conserva las ventanas ya abiertas para revisar los errores.
El arranque puede requerir descargas de Maven. Un timeout no mata procesos:
detenerlos antes de reintentar, incluso si terminaron de iniciar despues.
El acceso TCP a MySQL no prueba credenciales: cada servicio comprueba la
conexion y Hibernate valida su esquema al arrancar.

Las comprobaciones HTTP usan solo GET de disponibilidad/documentacion, no
login ni creacion de productos. Tener OpenAPI disponible y registros UP no
demuestra autorizacion JWT: ejecutar despues la matriz de
`../catalogo/SEGURIDAD_CATALOGO.md` en 9091 y 9090.
En http://127.0.0.1:8761 debe aparecer VENTA-SERVICE con instancia en 9094 y UP.
El lanzador no consulta rutas /internal, no genera JWT de servicio ni modifica
la seguridad de Gateway o CORS React/Vite. No inicia servicios heredados.

## Detener los cinco servicios

1. En la ventana de Gateway, pulsar Ctrl+C.
2. Repetir en Ventas, Catalogo, despues Identidad y finalmente Eureka.
3. Si cmd pregunta si desea terminar el trabajo por lotes, confirmar.
4. Cuando termine Maven/Java y vuelva el prompt, cerrar cada ventana.
5. Cerrar el lanzador. Cerrar solo el lanzador no detiene los servicios.

No se detiene MySQL ni se modifican sus datos.

## Verificacion de esta implementacion

Se revisan sintaxis PowerShell, parametros, variables heredadas, seleccion
explicita de configuraciones y orden de arranque sin ejecutar el lanzador.
No se afirma arranque real ni validacion HTTP hasta realizar la prueba local.
