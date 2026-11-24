# Creación administrativa de personal

## Contrato

`POST /api/v1/admin/usuarios` requiere Bearer JWT válido con
`role=ADMINISTRADOR`. Auth y Gateway aplican la misma regla al método y ruta
exactos, antes del cierre general. No se confía en cabeceras de rol arbitrarias.
Las demás operaciones administrativas no están implementadas.

Entrada: `nombre`, `email`, `password`, `role`.
- Nombre y correo obligatorios, máximo 255 caracteres; correo válido.
- Contraseña obligatoria y máximo 72 bytes UTF-8 mediante HashService.
- Role acepta exactamente ADMINISTRADOR o VENDEDOR, nunca CLIENTE ni alias.
- El rol del JSON es el de la cuenta nueva; no concede permisos al solicitante.
- No se reciben IDs de cuentas existentes ni se reutiliza la entidad como DTO.

Se crea una nueva fila User dentro de una transacción, con BCrypt y perfil en
la misma entidad. No hay llamadas a usuario-service. Si el correo ya existe,
incluido el de una cuenta CLIENTE, se devuelve 400 sin cambiar contraseña,
perfil ni rol. UNIQUE y el handler existente de integridad cubren colisiones
concurrentes; no se sobrescriben cuentas.

Respuesta 201 de ejemplo, con datos ficticios:

```json
{
  "id": 123,
  "nombre": "Personal de prueba",
  "email": "personal@example.test",
  "role": "VENDEDOR"
}
```

El DTO solo contiene esos cuatro campos; no incluye contraseña, hash ni
metadatos internos. No hay logs de credenciales en este flujo.

## Postman: comprobación manual pendiente

No se ha ejecutado el alta ni se han creado cuentas reales durante esta tarea.
El administrador inicial y las comprobaciones previas fueron informados por
el desarrollador; no demuestran el funcionamiento HTTP de este endpoint nuevo.

1. Mantener bootstrap deshabilitado: Auth normal, sin perfil `bootstrap-admin`
   ni `BOOTSTRAP_ADMIN_ENABLED=true`. No hace falta repetir la inicialización.
2. Reiniciar Auth y Gateway después de aplicar los cambios. La ruta local del
   Gateway y su `.example` apuntan a `lb://identidad-service`. No cambiar JWT_SECRET.
3. Obtener el token del administrador existente mediante el login normal.
4. POST `http://127.0.0.1:9090/api/v1/admin/usuarios`, Authorization > Bearer Token,
   Body > raw > JSON, Content-Type `application/json`:

```json
{
  "nombre": "Personal de prueba",
  "email": "personal@example.test",
  "password": "{{password_nueva_cuenta}}",
  "role": "VENDEDOR"
}
```

`{{password_nueva_cuenta}}` es una variable privada local de Postman, no una
contraseña de ejemplo para usar literalmente. No exportar/sincronizar su valor
ni el token. Para probar ADMINISTRADOR, usar otro correo y ese role.

Resultados esperados, todavía no ejecutados:

| Caso | Respuesta |
| --- | --- |
| ADMINISTRADOR válido, datos válidos y correo nuevo | 201, DTO seguro |
| Sin Bearer o JWT inválido/expirado | 401 |
| JWT válido de CLIENTE o VENDEDOR | 403 |
| Role CLIENTE, alias, omitido o inválido | 400 |
| Correo inválido, nombre vacío o contraseña mayor de 72 bytes UTF-8 | 400 |
| Correo existente, incluso CLIENTE | 400, sin elevar ni sobrescribir |
| Dos altas concurrentes con el mismo correo nuevo | Una creación; otra 400 |

Repetir los rechazos directamente contra `http://127.0.0.1:9096` para verificar
la seguridad local. Enviar `X-Role: ADMINISTRADOR` con un token CLIENTE no debe
alterar el 403. Comparar GET del perfil de una cuenta existente antes/después
de intentar registrar su correo para comprobar que se conserva.

Después de una futura alta autorizada, verificar login y GET del perfil de
la cuenta nueva; no publicar el JWT ni consultar/exportar hashes.
Registro público, login y GET/PATCH de perfil conservan sus rutas y contratos.
La autorización actual usa el rol firmado del JWT hasta su expiración; no
se incorpora revocación ni gestión de cambios de rol en esta tarea.
