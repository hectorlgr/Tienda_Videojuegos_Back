# Primer administrador en Docker

En el host Linux de AWS, usar Bash y Docker con la imagen aprobada de Identidad
ya cargada. Ejecutar desde la raíz del backend, con el servicio normal de
Identidad detenido y MySQL inicializado según [la guía MySQL](../mysql/README.md).
Usar una red privada, sin puertos publicados en MySQL. El puerto interno 9096
debe estar libre en su espacio de red. El lanzador usa los logs locales de Docker.

Definir `DB_CONTAINER` con el nombre del contenedor MySQL e `IDENTITY_IMAGE`
con la referencia verificada. Preparar un directorio externo al checkout,
accesible solo al operador, con cinco archivos de una línea, en UTF-8 y sin CR.
Materializarlos desde el gestor de secretos. `checkpoint_app` contiene la clave
de esa cuenta MySQL, `jwt` contiene el mismo secreto JWT del servicio normal
(al menos 32 bytes), y `email`, `nombre` y `password` contienen los datos
del primer administrador (contraseña de hasta 72 bytes UTF-8).

```bash
(
  set +x
  set -eu
  export DB_CONTAINER IDENTITY_IMAGE
  export DB_PASSWORD JWT_SECRET BOOTSTRAP_ADMIN_EMAIL BOOTSTRAP_ADMIN_NOMBRE BOOTSTRAP_ADMIN_PASSWORD
  DB_PASSWORD=$(< "$SECRET_DIR/checkpoint_app")
  JWT_SECRET=$(< "$SECRET_DIR/jwt")
  BOOTSTRAP_ADMIN_EMAIL=$(< "$SECRET_DIR/email")
  BOOTSTRAP_ADMIN_NOMBRE=$(< "$SECRET_DIR/nombre")
  BOOTSTRAP_ADMIN_PASSWORD=$(< "$SECRET_DIR/password")
  bash deploy/admin-bootstrap/run.sh
)
```

El código 0 confirma creación tras el commit. El 2 indica omisión por cuenta
existente o administrador previo. Para este último resultado, comprobar el
rol de la cuenta prevista antes de dar el acceso por habilitado. Cualquier
otro código exige revisión. El script detiene y retira su contenedor usando
el ID devuelto por Docker. Ante una interrupción abrupta, identificar por
nombre e imagen el contenedor residual antes de detenerlo y retirarlo.
El acceso al daemon permite inspeccionar las variables mientras existe el
contenedor. Restringirlo al operador.

El proceso comparte el espacio de red de MySQL (equivalente futuro en Compose
a `network_mode: service:mysql`). JDBC requiere TLS sobre 127.0.0.1 y el
servidor HTTP escucha solo en loopback. Eureka queda desactivado. Para el
arranque ordinario, seguir [la configuración backend](../backend/README.md)
en un contenedor independiente y omitir las variables y el perfil de bootstrap.
