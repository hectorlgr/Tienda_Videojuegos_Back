# Inicialización de MySQL 8.4

Desde la raíz del backend en Linux, usar la imagen oficial fijada por manifiesto
`linux/amd64`. Mantener la misma subred IPv4 en el despliegue y sus recreaciones.
Preparar una red bridge privada y un volumen de datos nuevo y exclusivo.
Definir `NETWORK`, `DATA_VOLUME`, `DB_CONTAINER` y `SECRET_DIR` con sus nombres
y la ruta absoluta de un directorio de secretos externo al repositorio.

Preparar en ese directorio tres archivos llamados `root`, `checkpoint_app` y
`checkpoint_ventas_app`. Cada uno contiene 32 bytes aleatorios expresados como
64 dígitos hexadecimales, con salto LF final opcional. Un gestor de secretos
puede generar ese formato, al igual que `openssl rand -hex 32`. Restringir la
lectura al operador y al usuario mysql del contenedor. Montar los archivos en
solo lectura. La imagen utiliza autenticación nativa de MySQL 8.4
(`caching_sha2_password`).

```bash
IMAGE='mysql:8.4.8@sha256:4fc4bca5a26c5aa2246a92779d238bc2d34f0373e65dc083d7b635b02780dd3d'
SUBNET=$(docker network inspect --format '{{(index .IPAM.Config 0).Subnet}}' "$NETWORK")
docker run -d --platform linux/amd64 --name "$DB_CONTAINER" --network "$NETWORK" \
  --mount "type=volume,src=$DATA_VOLUME,dst=/var/lib/mysql,volume-nocopy" \
  --mount "type=bind,src=$SECRET_DIR,dst=/run/secrets,readonly" \
  --mount "type=bind,src=$PWD/deploy/mysql/start.sh,dst=/checkpoint/start.sh,readonly" \
  --mount "type=bind,src=$PWD/deploy/mysql/init,dst=/docker-entrypoint-initdb.d,readonly" \
  --mount "type=bind,src=$PWD/docs/identidad/ESQUEMA_IDENTIDAD.md,dst=/checkpoint/ESQUEMA_IDENTIDAD.md,readonly" \
  --mount "type=bind,src=$PWD/catalogo-service/sql,dst=/checkpoint/catalogo,readonly" \
  --mount "type=bind,src=$PWD/venta-service/sql,dst=/checkpoint/ventas,readonly" \
  -e "CHECKPOINT_MYSQL_NETWORK=$SUBNET" --entrypoint bash "$IMAGE" /checkpoint/start.sh
```

Para Compose, conservar la referencia, el comando de arranque, la subred y los
montajes. El entrypoint oficial ejecuta el hook con la red TCP deshabilitada.
El hook extrae el bloque SQL de Identidad, aplica los archivos versionados en
orden y crea las cuentas. La consulta final de Catálogo 03 también muestra el
índice compuesto RAWG incluido en el DDL base. Catálogo 05 distingue ese índice
de un UNIQUE sobre `rawg_id` solo.

Para comprobar disponibilidad, desde el espacio de red de MySQL ejecutar
`mysql --protocol=TCP -h127.0.0.1 -ucheckpoint_app -p` e introducir la clave
cuando se solicite. Verificar `SELECT CURRENT_USER();` y consultar
`checkpoint_store_identidad.users`. Ese es el acceso local previsto para el
bootstrap futuro. Los servicios se conectan por el nombre del contenedor desde
la subred autorizada.

El marcador `/var/lib/mysql/.checkpoint-init-v1` se publica mediante renombrado
después del último permiso. En un volumen completo, MySQL reutiliza los datos
y las credenciales existentes. Si falla el arranque, revisar `docker logs` y el
estado de salida. Conservar el volumen parcial para diagnóstico, corregir la
causa y volver a ejecutar con otro volumen vacío. El DDL puede haber hecho
commit. Fabricar el marcador o repetir los scripts manualmente ocultaría ese
estado parcial.
