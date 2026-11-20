# Arranque local de CheckPoint Store

## Doble clic

1. Tener Java 21 disponible y MySQL 8.4 iniciado en 127.0.0.1:3306.
   Las bases `checkpoint_store_identidad` y `checkpoint_store_catalogo`
   deben existir con sus esquemas previamente preparados. La cuenta existente
   `checkpoint_app` debe tener permisos de operacion sobre ambas.
2. Detener cualquier entorno anterior que ocupe 8761, 9096, 9091 o 9090.
3. Hacer doble clic en `iniciar_checkpoint_local.bat`, en la raiz del backend.
4. Escribir una sola vez la contrasena MySQL de checkpoint_app cuando se
   solicite (entrada oculta). No se solicita ni muestra JWT_SECRET.
5. Esperar el mensaje `Entorno disponible`. Se abren cuatro ventanas:
   Eureka, Identidad, Catalogo y Gateway.
6. Volver a iniciar sesion en Postman: cada ejecucion genera una nueva clave
   compartida y los tokens del entorno anterior ya no sirven.

El nuevo BAT invoca el PowerShell existente con `-IncluirCatalogo`. No hay
una segunda implementacion del arranque. `iniciar_identidad.bat` e
`iniciar_servicios.bat` no se modificaron: el modo sin parametro sigue
iniciando solo Eureka, Identidad y Gateway con sus configuraciones anteriores.
No iniciar ambos lanzadores a la vez.

## Configuracion seleccionada y variables

El modo de cuatro servicios selecciona explicitamente archivos existentes
mediante SPRING_CONFIG_LOCATION heredado por cada proceso:

| Servicio | Puerto | Configuracion |
| --- | --- | --- |
| Eureka | 8761 | eureka-server/src/main/resources/application.properties |
| Identidad | 9096 | identidad-service/src/main/resources/application.properties.example |
| Catalogo | 9091 | catalogo-service/src/main/resources/application.properties.example |
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
- JWT_SECRET se genera una sola vez con 64 bytes aleatorios criptograficos
  convertidos a texto Base64; Identidad, Catalogo y Gateway heredan ese mismo
  texto. Los servicios usan sus bytes UTF-8, sin decodificarlo como Base64.
- Eureka se inicia antes de establecer credenciales o JWT_SECRET.
- Gateway se inicia despues de retirar las credenciales MySQL del entorno
  del lanzador. Los hijos ya iniciados conservan su propia copia.
- BOOTSTRAP_ADMIN_ENABLED=false; ddl-auto=validate; sql.init.mode=never;
  Liquibase y Flyway deshabilitados. No se crean usuarios, tablas ni datos.
- El modo nuevo neutraliza overrides habituales de configuracion/perfiles
  y restaura las variables del proceso lanzador al salir, tambien ante fallos.

Las credenciales y la clave solo se transmiten por entorno heredado, nunca
mediante archivos, argumentos Maven, mensajes ni logs del lanzador. No activar
trazas de entorno o volcados de configuracion al investigar errores.
El usuario local del sistema operativo sigue pudiendo inspeccionar sus procesos.

## Orden y comprobaciones

1. Comprobar wrappers, archivos de configuracion y puertos libres (incluidos
   listeners IPv6). Comprobar disponibilidad TCP de MySQL, sin ejecutar SQL.
2. Iniciar Eureka y esperar HTTP 200 en 8761.
3. Iniciar Identidad, esperar su OpenAPI y registro IDENTIDAD-SERVICE en estado UP.
4. Iniciar Catalogo, esperar su OpenAPI y registro CATALOGO-SERVICE en estado UP.
5. Iniciar Gateway, esperar su ruta OpenAPI hacia Identidad, registro
   API-GATEWAY en estado UP y ruta OpenAPI hacia Catalogo.

Cada espera tiene un limite de 180 segundos. Si hay un fallo, el lanzador
informa la etapa y conserva las ventanas ya abiertas para revisar los errores.
El arranque puede requerir descargas de Maven. Un timeout no mata procesos:
detenerlos antes de reintentar, incluso si terminaron de iniciar despues.
El acceso TCP a MySQL no prueba credenciales: cada servicio comprueba la
conexion y Hibernate valida su esquema al arrancar.

Las comprobaciones HTTP usan solo GET de disponibilidad/documentacion, no
login ni creacion de productos. Tener OpenAPI disponible y registros UP no
demuestra autorizacion JWT: ejecutar despues la matriz de
`catalogo-service/SEGURIDAD_CATALOGO.md` en 9091 y 9090.

## Detener los cuatro servicios

1. En la ventana de Gateway, pulsar Ctrl+C.
2. Repetir en Catalogo, despues Identidad y finalmente Eureka.
3. Si cmd pregunta si desea terminar el trabajo por lotes, confirmar.
4. Cuando termine Maven/Java y vuelva el prompt, cerrar cada ventana.
5. Cerrar el lanzador. Cerrar solo el lanzador no detiene los servicios.

No se detiene MySQL ni se modifican sus datos.

## Verificacion de esta implementacion

Se revisan sintaxis PowerShell, parametros, variables heredadas, seleccion
explicita de configuraciones y orden de arranque sin ejecutar el lanzador.
No se afirma arranque real ni validacion HTTP hasta realizar la prueba local.
