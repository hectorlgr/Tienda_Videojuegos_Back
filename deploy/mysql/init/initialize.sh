#!/usr/bin/env bash
# La imagen oficial puede ejecutar o cargar el hook según los permisos de montaje.
(
    set -euo pipefail
    set +x
    export LC_ALL=C
    network=${CHECKPOINT_MYSQL_NETWORK:?Indicar la subred IPv4 Docker en CIDR}
    [[ $network =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}/([1-9]|[12][0-9]|3[0-2])$ ]] || {
        echo 'Subred Docker inválida.' >&2
        exit 1
    }
    sql() {
        mysql --defaults-extra-file=<(printf '[client]\npassword=%s\n' "$MYSQL_ROOT_PASSWORD") \
            --protocol=socket --user=root --default-character-set=utf8mb4 --batch "$@"
    }
    sql <<'SQL'
CREATE DATABASE checkpoint_store_identidad CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE checkpoint_store_catalogo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SQL
    awk '
        { sub(/\r$/, "") }
        /^```sql$/ { blocks++; active=1; next }
        active && /^```$/ { active=0; next }
        active { print }
        END { if (active || blocks != 1) exit 1 }
    ' /checkpoint/ESQUEMA_IDENTIDAD.md | sql checkpoint_store_identidad
    sql -e 'SELECT id FROM checkpoint_store_identidad.users LIMIT 0'
    for file in \
        catalogo/crear_productos_checkpoint_store_catalogo.sql \
        catalogo/03_validar_producto_formulario.sql \
        catalogo/04_exigir_producto_completo.sql \
        catalogo/05_validar_variantes_rawg.sql \
        catalogo/07_crear_operaciones_stock.sql \
        ventas/01_crear_checkpoint_store_ventas.sql \
        ventas/02_crear_tablas_pedidos.sql \
        ventas/03_crear_solicitudes_pedido.sql \
        ventas/04_ampliar_solicitudes_pedido_checkout.sql
    do
        sql < "/checkpoint/$file"
        printf 'Aplicado %s\n' "$file"
    done

    create_account() {
        local user=$1 host=$2 file=/run/secrets/$1 password
        # Detecta también bytes NUL, líneas adicionales y finales CRLF.
        awk '$0 !~ /^[[:xdigit:]]{64}$/ { bad=1 } END { exit (NR != 1 || bad) }' "$file" || {
            echo "Formato de secreto inválido para $user." >&2
            exit 1
        }
        password=$(< "$file")
        # Solo literales validados. El cliente recibe SQL por stdin.
        if ! printf "CREATE USER '%s'@'%s' IDENTIFIED BY '%s';\n" "$user" "$host" "$password" | sql 2>/dev/null; then
            echo "Falló la creación de $user." >&2
            exit 1
        fi
    }
    for host in "$network" 127.0.0.1; do
        create_account checkpoint_app "$host"
        for schema in 'checkpoint\_store\_identidad' 'checkpoint\_store\_catalogo'; do
            printf "GRANT SELECT, INSERT, UPDATE, DELETE ON \`%s\`.* TO 'checkpoint_app'@'%s';\n" "$schema" "$host" | sql
        done
    done
    create_account checkpoint_ventas_app "$network"
    sql <<SQL
GRANT SELECT ON \`checkpoint\_store\_ventas\`.* TO 'checkpoint_ventas_app'@'$network';
GRANT INSERT ON checkpoint_store_ventas.pedidos TO 'checkpoint_ventas_app'@'$network';
GRANT INSERT ON checkpoint_store_ventas.detalle_pedidos TO 'checkpoint_ventas_app'@'$network';
GRANT INSERT, UPDATE ON checkpoint_store_ventas.solicitudes_pedido TO 'checkpoint_ventas_app'@'$network';
SQL
    printf 'complete\n' > /var/lib/mysql/.checkpoint-init-v1.tmp
    mv /var/lib/mysql/.checkpoint-init-v1{.tmp,}
)
