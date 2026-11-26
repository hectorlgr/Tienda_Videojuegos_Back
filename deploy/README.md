# CheckPoint Store en Ubuntu amd64

Desde un directorio de trabajo en Ubuntu 24.04, con Git, Bash, OpenSSL, Docker Engine y el complemento Compose con soporte para `up --wait`:

```bash
git clone https://github.com/hectorlgr/Tienda_Videojuegos_Back.git checkpoint-store-backend
git clone https://github.com/hectorlgr/Tienda_Videojuegos_Front.git checkpoint-store-frontend
cd checkpoint-store-backend
# Seleccionar la revisión entregada que contiene deploy/compose.yaml.
cp -n deploy/.env.example deploy/.env
dc() { docker compose -p checkpoint --env-file deploy/.env -f deploy/compose.yaml "$@"; }
```

La etapa se entrega como cambios locales pendientes de versionar. Para reproducirla desde clones antes de publicarla, copiar los tres archivos de esta entrega a `deploy/` del backend. Las revisiones base ensayadas son `626b35b` del backend y `4255937` del frontend.

Editar `deploy/.env`. Usar el origen HTTP completo de la tienda, con puerto si corresponde y sin ruta ni barra final. `VITE_API_BASE_URL` se incorpora al bundle en la compilación. Reconstruir el frontend si cambia. `CATALOGO_PUBLIC_BASE_URL` y `CORS_PUBLIC_ORIGIN` se aplican al crear los contenedores. Los tres heredan `PUBLIC_ORIGIN` y admiten valores independientes. Para acceso público, establecer `WEB_BIND=0.0.0.0`, ajustar `WEB_PORT` y usar el DNS público en el origen. La ruta de `FRONTEND_REPO` se resuelve desde `deploy/`.

### Secretos

Usar la misma ruta absoluta en `SECRET_DIR` y en los comandos siguientes. Ejecutar este bloque **solo para una instalación nueva**. El directorio privado y los archivos deben conservarse al reiniciar. En un entorno existente, recuperar los originales desde almacenamiento protegido.

```bash
export SECRET_DIR="$HOME/.checkpoint/secrets"
(
  set -euo pipefail
  set +x
  umask 077
  mkdir -p "$(dirname "$SECRET_DIR")"
  mkdir -m 700 "$SECRET_DIR"
  for name in root checkpoint_app checkpoint_ventas_app jwt stock password; do
    openssl rand -hex 32 | tr -d '\n' > "$SECRET_DIR/$name"
  done
  touch "$SECRET_DIR/rawg"
  read -rp 'Email del primer administrador: ' email
  read -rp 'Nombre del primer administrador: ' nombre
  printf '%s' "$email" > "$SECRET_DIR/email"
  printf '%s' "$nombre" > "$SECRET_DIR/nombre"
  dc pull mysql
  mysql_image=$(dc config --images mysql)
  mysql_gid=$(docker run --rm --network none --entrypoint id "$mysql_image" -g mysql)
  sudo chgrp "$mysql_gid" "$SECRET_DIR"/{root,checkpoint_app,checkpoint_ventas_app}
  chmod 440 "$SECRET_DIR"/{root,checkpoint_app,checkpoint_ventas_app}
)
```

Los montajes individuales permiten al usuario mysql leer sus tres archivos dentro del contenedor. Spring importa las credenciales y `JWT_SECRET` mediante `configtree`. Catálogo y Ventas exportan además el archivo `stock` a `STOCK_SERVICE_JWT_SECRET` porque su código consulta el entorno del proceso. El archivo `rawg` vacío desactiva el uso de una clave RAWG. Para habilitarla, materializar la clave sin salto de línea desde el gestor de secretos y recrear Catálogo. Guardar la contraseña administrativa generada como credencial del operador.

### Primera instalación

```bash
dc config --quiet
dc build
dc up -d --wait mysql
```

El volumen nuevo se inicializa con el entrypoint, el hook y los SQL aprobados. Si MySQL sale con error, consultar `dc logs mysql`; el [procedimiento de recuperación](mysql/README.md) conserva el volumen parcial para diagnóstico. Mantener estable `DB_SUBNET` porque forma parte de las cuentas MySQL. Comprobar antes del primer arranque que ambas subredes estén libres. Si se ajusta `WEB_SUBNET`, elegir `GATEWAY_WEB_IP` dentro de ella.

Con Identidad detenida y la imagen construida, ejecutar explícitamente desde la raíz del backend:

```bash
(
  set -eu
  set +x
  export DB_CONTAINER IDENTITY_IMAGE DB_PASSWORD JWT_SECRET
  export BOOTSTRAP_ADMIN_EMAIL BOOTSTRAP_ADMIN_NOMBRE BOOTSTRAP_ADMIN_PASSWORD
  DB_CONTAINER=$(dc ps -q mysql)
  IDENTITY_IMAGE=checkpoint-identidad
  DB_PASSWORD=$(< "$SECRET_DIR/checkpoint_app")
  JWT_SECRET=$(< "$SECRET_DIR/jwt")
  BOOTSTRAP_ADMIN_EMAIL=$(< "$SECRET_DIR/email")
  BOOTSTRAP_ADMIN_NOMBRE=$(< "$SECRET_DIR/nombre")
  BOOTSTRAP_ADMIN_PASSWORD=$(< "$SECRET_DIR/password")
  bash deploy/admin-bootstrap/run.sh
)
```

El código 0 confirma la creación. Si devuelve 2, verificar el rol de la cuenta existente antes de continuar. El lanzador elimina su contenedor temporal.

### Operación

```bash
dc up -d --wait
dc ps -a
dc logs --tail=80 gateway
docker stats --no-stream $(dc ps -q)
curl -fsS --retry 15 --retry-delay 5 http://127.0.0.1:8080/api/productos -o /dev/null
dc stop
# Para reanudar, también después de encender EC2:
dc up -d --wait
```

Ajustar el puerto de la prueba al de Nginx. MySQL comprueba el marcador final y la disponibilidad TCP. Las sondas Java verifican la escucha y Nginx responde por HTTP. La ruta pública puede devolver 503 hasta que el Gateway actualice su registro Eureka. Un 503 persistente requiere revisar los registros de ambos. Nginx resuelve `gateway` al arrancar y Compose conserva la IP de ese servicio en la red web durante las recreaciones. Las conexiones JDBC y el descubrimiento usan DNS en la segunda red.

Detener el proyecto antes de apagar EC2 y ejecutar el arranque indicado al volver. MySQL usa reinicio manual para que un fallo de inicialización quede visible. Los otros seis servicios usan `unless-stopped`. Tras un apagado imprevisto, levantar MySQL y comprobar el estado del conjunto con Compose. Los volúmenes de MySQL e imágenes y los archivos de secretos se reutilizan conservando el nombre del proyecto. `down -v` eliminaría los volúmenes. Docker rota los logs de los servicios a tres archivos de 10 MiB. Las JVM reservan hasta el 50 % del límite para heap.

### Reutilización de la prueba local 2.3.6

En Docker Desktop se conservaron las imágenes y los volúmenes del proyecto `checkpoint236`. Desde PowerShell en la raíz del backend:

```powershell
$env:SECRET_DIR = (Join-Path $env:TEMP 'checkpoint-236-626b35b').Replace('\','/')
$env:PUBLIC_ORIGIN = 'http://127.0.0.1:18436'
$env:WEB_PORT = '18436'
function dc { docker compose -p checkpoint236 -f deploy/compose.yaml @args }
dc up -d --wait
# Para reconstruir las seis imágenes desde los clones:
dc build
dc stop
```

Conservar el directorio privado de secretos antes de limpiar temporales. La primera instalación de Ubuntu genera sus propias credenciales. Para la etapa 2.3.7 local se reutiliza el administrador ya creado.
