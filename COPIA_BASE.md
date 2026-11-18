# Copia base de CheckPoint Store

Origen: carpeta hermana `Backend Ferreteria - copia`.

Se conservaron los 12 modulos originales, sus fuentes Java, pruebas existentes,
POM, Maven Wrapper, Dockerfile, changelogs y documentacion sin cambios.
No se implementaron fusiones ni funcionalidades nuevas.

## Exclusiones

- Todos los `target/`: artefactos de compilacion y pruebas.
- `logs/`: registros generados.
- `.vscode/`: configuracion local del editor.
- `catalogo-service/objmapper.txt`: salida de inspeccion de clases.
- `docker-compose.yml`: contiene una clave JWT fija; se omitio sin modificarlo
  ni crear otro Compose en esta etapa.
- `auth-service/src/main/resources/application.properties` y
  `api-gateway/src/main/resources/application.yml`: contienen una clave JWT fija.
  Se proporcionan ejemplos inactivos con sufijo `.example` y variables sin
  valores secretos. No son configuraciones habilitadas.

La copia excluye `.git` a cualquier profundidad, enlaces, archivos temporales
y almacenes de claves. No se encontro un directorio `.git` en el recorrido.
Los `.gitignore` y `.gitattributes` originales se conservan como archivos de texto;
esto no inicializa un repositorio.

## Revision pendiente antes de ejecutar

- Definir configuraciones propias de Auth y Gateway y revisar Compose.
- Las configuraciones restantes conservan las conexiones locales originales:
  usuario `root`, contrasena vacia, nombres de bases de ferreteria y opciones de
  inicializacion. No se detectaron contrasenas no vacias en ellas. Deben revisarse
  antes de conectar esta copia a una base de datos.
- Las claves y contrasenas ficticias de pruebas y ejemplos Swagger permanecen
  en el codigo original; no son las credenciales operativas omitidas.
- README y scripts heredados se conservan como referencia, no como instrucciones
  aprobadas para esta nueva copia. `build-all.bat` contiene eliminacion global de
  contenedores e imagenes Docker. Ningun script fue ejecutado.

Esta carpeta es una base para revisar y migrar, no un despliegue configurado.
No se inicializo Git, no se configuraron remotos y no se crearon commits.

## Revision previa a Git

Se verificaron los 12 modulos y la ausencia de entradas `.git`, tanto directorios
como archivos de enlace a repositorios. Los ejemplos de Auth y Gateway existen;
sus configuraciones activas y el Compose siguen ausentes.

Se agrego un `.gitignore` en la raiz. Los archivos de configuracion operativa de
Spring quedan excluidos, salvo la configuracion de Eureka, revisada sin secretos.
Las nueve configuraciones de base de datos heredadas permanecen en disco sin
cambios, pero no se incorporarian normalmente a Git. Antes de reproducir el
proyecto desde un futuro clon se necesitaran ejemplos seguros de esas nueve
configuraciones o una configuracion externa equivalente.

Se excluyen tambien artefactos Maven, logs, temporales, ajustes locales, `.env`
y archivos habituales de claves o credenciales. Los archivos `.example` seguros
se conservan. El ignore no detecta secretos incrustados en codigo ni sustituye
la revision del contenido antes de incorporar archivos.

La revision estatica no detecto credenciales operativas reales en los archivos
candidatos. Si existen literales de demostracion: una clave JWT fija y un token
deliberadamente falso en `JwtServiceTest.java`, contrasenas de pruebas y ejemplos
Swagger. No se muestran sus valores ni se modifica Java. Por tanto, no se afirma
que el codigo este libre de claves o contrasenas literales: estos ejemplos deben
clasificarse explicitamente al revisar alertas de secretos antes del primer commit.

### Configuracion pendiente para ejecucion

- `auth-service/src/main/resources/application.properties`: existe su `.example`;
  requiere `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET` externos.
- `api-gateway/src/main/resources/application.yml`: existe su `.example`;
  requiere el mismo `JWT_SECRET` que Auth.
- `docker-compose.yml`: omitido por contener la clave original. Se necesita
  preparar una version segura si se elige ejecutar con Compose; no es necesario
  para ejecucion manual.
- Configurar bases propias para los nueve servicios con configuracion heredada
  de MySQL. Sus archivos actuales existen, pero siguen apuntando al entorno
  original. No se crearon ni modificaron bases de datos.

Solo se agrego el ignore raiz y se amplio este documento durante esta revision.

En la comprobacion final tambien aparecieron recursos bajo `api-gateway/target`
y `auth-service/target`. No fueron generados por comandos de esta revision y no
se eliminaron; quedan excluidos del futuro versionado mediante `target/`.
La exclusion de `target/` descrita arriba corresponde a la copia inicial, no
a una garantia de que otras herramientas no generen esa carpeta posteriormente.
