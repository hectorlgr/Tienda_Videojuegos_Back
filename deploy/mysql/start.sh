#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob dotglob
data=/var/lib/mysql
marker=$data/.checkpoint-init-v1
entries=("$data"/*)
if (( ${#entries[@]} )); then
    if [[ ! -d $data/mysql || ! -f $marker || $(< "$marker") != complete ]]; then
        echo 'Volumen no vacío sin inicialización completa. Arranque bloqueado.' >&2
        exit 1
    fi
else
    hooks=(/docker-entrypoint-initdb.d/*)
    [[ ${#hooks[@]} == 1 && ${hooks[0]} == /docker-entrypoint-initdb.d/initialize.sh && -s ${hooks[0]} ]] || {
        echo 'Falta el inicializador exclusivo de CheckPoint.' >&2
        exit 1
    }
    # El entrypoint oficial interpola esta clave en SQL y en un fichero de cliente.
    # Comprobar todos los bytes antes de entregársela.
    export MYSQL_ROOT_PASSWORD_FILE=/run/secrets/root
    LC_ALL=C awk '$0 !~ /^[[:xdigit:]]{64}$/ { bad=1 } END { exit (NR != 1 || bad) }' "$MYSQL_ROOT_PASSWORD_FILE" || {
        echo 'Formato de secreto inválido para root.' >&2
        exit 1
    }
fi
export MYSQL_ROOT_HOST=localhost
unset MYSQL_RANDOM_ROOT_PASSWORD MYSQL_ALLOW_EMPTY_PASSWORD MYSQL_USER MYSQL_DATABASE MYSQL_ONETIME_PASSWORD MYSQL_ROOT_PASSWORD
exec docker-entrypoint.sh mysqld --skip-name-resolve
