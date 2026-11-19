# Cuenta y perfil unificados: esquema inicial

Propuesta para una base **nueva** de CheckPoint Store. Este documento no se
ejecuta automáticamente ni migra tablas existentes. No importar usuarios,
perfiles ni contraseñas del proyecto anterior.

```sql
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    telefono VARCHAR(30) NULL,
    direccion VARCHAR(255) NULL,
    region VARCHAR(100) NULL,
    comuna VARCHAR(100) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

- `id` corresponde al `Long` de Java. Los demás campos son `String`.
- Los tamaños coinciden con las anotaciones JPA; donde no se especifica
  longitud se utiliza el valor predeterminado de 255.
- `password` admite el hash BCrypt completo (60 caracteres). Nunca almacenar
  contraseñas en claro. El límite de entrada sigue siendo 72 bytes UTF-8.
- Nombre, correo, contraseña y rol son obligatorios. El registro público
  asigna `CLIENTE`; los demás datos de perfil quedan inicialmente en NULL.
- La colación permite textos en español y emojis; la unicidad del correo
  sigue las comparaciones de MySQL, sin distinguir mayúsculas ni acentos
  con esta colación.
- El registro persiste una sola fila mediante una transacción Spring.
  UNIQUE protege también solicitudes concurrentes; una colisión devuelve
  HTTP 400 y no reemplaza la cuenta existente.
- El ejemplo de configuración de Auth aún contiene `ddl-auto=update`.
  No usarlo para reconvertir una base heredada: preparar una base nueva y
  comprobar su esquema antes de habilitar el servicio. No se ha ejecutado
  este SQL ni se ha modificado ninguna base.

## Estado temporal

Auth ya no crea perfiles en `usuario-service`. Ventas todavía consulta ese
servicio y no encontrará allí las nuevas cuentas. No replicar ni sincronizar
filas como solución provisional.

Quedan pendientes los endpoints de perfil propio, los DTO de respuesta
sin datos sensibles, la autenticación por ID estable en el JWT y la adaptación
de Ventas. El JWT actual mantiene el correo como subject y los roles actuales
no se modifican en esta etapa.
