#!/usr/bin/env bash
set -euo pipefail
set +x
export MSYS_NO_PATHCONV=1
: "${DB_CONTAINER:?Falta el contenedor MySQL}" "${IDENTITY_IMAGE:?Falta la imagen de Identidad}"
variables=(DB_PASSWORD JWT_SECRET BOOTSTRAP_ADMIN_EMAIL BOOTSTRAP_ADMIN_NOMBRE BOOTSTRAP_ADMIN_PASSWORD)
args=()
for key in "${variables[@]}"; do
    [[ -n ${!key:-} ]] || { echo "Falta $key" >&2; exit 1; }
    export "${key?}"
    args+=(-e "$key")
done
db=$(docker inspect --type container --format '{{.Id}}' "$DB_CONTAINER")
# Un nombre por instancia MySQL impide dos ejecuciones simultáneas del procedimiento.
id=$(docker create --pull=never --name "checkpoint-bootstrap-${db:0:12}" \
    --network "container:$db" --restart=no "${args[@]}" \
    -e 'SPRING_CONFIG_LOCATION=classpath:/application.properties.example[.properties]' \
    -e SPRING_PROFILES_ACTIVE=bootstrap-admin -e BOOTSTRAP_ADMIN_ENABLED=true \
    -e SERVER_ADDRESS=127.0.0.1 -e DB_USERNAME=checkpoint_app \
    -e 'DB_URL=jdbc:mysql://127.0.0.1:3306/checkpoint_store_identidad?serverTimezone=UTC&sslMode=REQUIRED' \
    -e EUREKA_CLIENT_ENABLED=false -e SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
    -e SPRING_SQL_INIT_MODE=never "$IDENTITY_IMAGE")
cleanup() {
    status=$?
    docker stop --time 20 "$id" >/dev/null || status=1
    docker rm "$id" >/dev/null || status=1
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
docker start "$id" >/dev/null
deadline=$((SECONDS + 180))
while (( SECONDS < deadline )); do
    logs=$(docker logs "$id" 2>&1)
    case "$logs" in
        *'Inicialización administrativa completada. Detener Auth y retirar la habilitación temporal.'*)
            echo 'Administrador creado.'; exit 0 ;;
        *'Inicialización omitida: cuenta existente o administrador ya presente. No se modificaron cuentas.'*)
            echo 'Bootstrap omitido por cuenta existente o administrador previo.'; exit 2 ;;
    esac
    [[ $(docker inspect -f '{{.State.Running}}' "$id") == true ]] || break
    sleep 1
done
echo 'El runner no confirmó el resultado. Verificar el estado en MySQL antes de reintentar.' >&2
exit 1
