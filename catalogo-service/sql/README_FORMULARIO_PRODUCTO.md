# Evolución manual del modelo de productos — MySQL 8.4

**Actualización posterior:** los DTO y el servicio ya se adaptaron al formulario.
El contrato vigente está en `../FORMULARIO_PRODUCTOS_HTTP.md`, incluyendo
plataforma obligatoria para VIDEOJUEGO y semántica de la galería.
El grupo ProductoCompleto ahora se valida explícitamente en altas/ediciones,
junto con Default; sigue sin activarse automáticamente en JPA.
Las referencias de abajo a la adaptación HTTP pendiente describen la fase
original del esquema. Antes de habilitar creación definitiva todavía es
necesario completar datos, revisar 03 y aplicar 04: el código no crea ese
índice ni garantiza unicidad concurrente sin él. El CHECK de 04 no exige
plataforma; esa regla adicional se aplica en DTO/servicio.

Scripts preparados, NO ejecutados. No se ha validado el esquema en MySQL.
Hibernate debe seguir con ddl-auto=validate; no crear/actualizar tablas automáticamente.

## Decisión de compatibilidad

Los endpoints actuales no reciben codigo, formato, ecosistema, compatibilidad
ni franquicia. Exigirlos ahora mediante NOT NULL o validación automática rompería
POST y, para filas antiguas incompletas, PUT y movimientos de stock.

Por eso esta entrega tiene dos fases explícitas:

1. **Transitoria, aplicable con el código actual:** columnas nullable, límites
   de longitud, formato limitado a FISICO/DIGITAL y stockCritico no negativo.
   SKU todavía NO es único ni obligatorio en la base durante esta fase.
2. **Final, pendiente de la adaptación HTTP/formulario:** codigo NOT NULL +
   UNIQUE y obligatoriedad condicional por categoría. Se entrega el script 04,
   pero NO se debe ejecutar con los DTO actuales.

El modelo incorpora el grupo Bean Validation `ProductoCompleto`: exige código
no vacío y formato/ecosistema/compatibilidad/franquicia según la categoría.
Todavía no se activa en JPA ni en el servicio. La futura adaptación deberá
validar ambos grupos, Default y ProductoCompleto. El mapping de codigo permanece
nullable durante la transición; se alineará a nullable=false y la unicidad
cuando se active la fase final. No usar ddl-auto=update para ello.

## Orden exacto en Workbench: base existente

1. Detener Catálogo y cualquier otro escritor; respaldar la base.
2. Inspeccionar con SHOW CREATE TABLE checkpoint_store_catalogo.productos:
   debe existir el esquema anterior con las cuatro categorías y rawg_id.
   Si faltara categoria, revisar primero agregar_categoria_productos.sql.
   Confirmar InnoDB y ausencia de columnas/tabla nuevas para no repetir DDL.
3. Abrir y ejecutar **01_ampliar_producto_formulario.sql** UNA vez.
   Agrega columnas nullable y crea producto_imagenes vacía. No hace UPDATE,
   no rellena productos ni cambia valores existentes.
4. Verificar con SHOW CREATE TABLE ambas tablas; arrancar el nuevo código con
   ddl-auto=validate. El código ampliado no puede arrancar contra el esquema
   antiguo: necesita esas columnas y la tabla de imágenes.
5. Ejecutar **02_preparar_codigos_producto.sql**: solo SELECT, revisar propuestas.
   Preparar/aprobar la asignación manual por ID. El archivo contiene una plantilla
   UPDATE comentada, no un backfill automático. No ejecutar la plantilla literalmente.
   Comprobar colisiones con códigos ya asignados. Completar también los campos
   específicos según información real; nunca inferir FISICO/DIGITAL.
6. Ejecutar **03_validar_producto_formulario.sql** después de completar los datos.
   Resolver faltantes y duplicados; todas las consultas de incidencias deben
   devolver cero filas. Los productos ocultos también cuentan.
7. **DETENERSE AQUÍ hasta adaptar DTO, servicio y formulario en otra tarea.**
8. Cuando la aplicación ya envíe todos los campos requeridos, detener escritores,
   repetir 03 y ejecutar **04_exigir_producto_completo.sql** UNA vez.
9. Revisar SHOW CREATE TABLE, reiniciar Catálogo y verificar el nuevo contrato HTTP
   y restricciones en MySQL. Estas comprobaciones no se hicieron en esta tarea.

DDL MySQL hace commit implícito. 01 contiene dos sentencias: si la segunda falla,
la primera puede haber quedado aplicada. Inspeccionar SHOW CREATE TABLE y
ejecutar únicamente la sentencia pendiente; no repetir todo ni borrar tablas.
No hay DROP/TRUNCATE ni eliminación de datos en estos scripts.

## Instalación nueva

En la base vacía, ejecutar **crear_productos_checkpoint_store_catalogo.sql**.
Ya contiene las nuevas columnas, restricciones transitorias y producto_imagenes.
NO ejecutar 01 ni agregar_categoria_productos.sql después.
Cuando se adapte el contrato del formulario, seguir 02 (si hay filas sin SKU),
03 y 04 para pasar a las restricciones finales. Crear desde cero produce
deliberadamente el mismo esquema transitorio que la actualización de la base.

## Campos y futuro formulario React

| Campo | Persistencia | Regla final |
| --- | --- | --- |
| codigo | VARCHAR(64) | Obligatorio, único, estable; no recalcular al editar categoría |
| descripcionCorta | descripcion_corta VARCHAR(500) | Opcional |
| descripcion | TEXT existente | Descripción larga, sin migrar ni truncar contenido |
| formato | VARCHAR(10) | FISICO o DIGITAL; obligatorio para VIDEOJUEGO |
| ecosistema | VARCHAR(100) | Obligatorio para CONSOLA |
| compatibilidad | VARCHAR(255) | Obligatorio para ACCESORIO |
| franquicia | VARCHAR(255) | Obligatorio para FIGURA |
| stockCritico | stock_critico INT | NULL o >=0, no altera stock/disponibilidad |

Las columnas específicas permiten NULL en todas las fases; el CHECK final
solo exige el campo correspondiente a la categoría. No exige vaciar los otros.
La futura UI puede ocultar campos que no correspondan y enviar null. Un cambio
de categoría requerirá completar el campo de destino cuando se active la fase final.
plataforma, genero e imagenUrl siguen intactos; no se copian ni renombran.

SKU: collation explícita utf8mb4_0900_ai_ci, por lo que UNIQUE tratará como
iguales códigos que solo difieran por mayúsculas o acentos. La futura entrada
debería recortar espacios y normalizar mayúsculas. Los prefijos JUE/CON/ACC/FIG
son una convención, no un CHECK que fuerce renombrar códigos.
El SQL de propuestas usa el ID local y no trunca IDs de cuatro o más dígitos.

rawg_id sigue opcional y SIN UNIQUE. Variantes pueden compartirlo con SKU,
plataforma, formato, precio y stock independientes. No se modifica RAWG.
La recomendación anterior de un índice único rawg_id queda descartada.

## Imágenes adicionales

imagenUrl sigue siendo la principal. producto_imagenes guarda hasta cuatro
adicionales (máximo total: principal + cuatro), con:

- ID propio BIGINT, producto_id obligatorio, url VARCHAR(2048), orden INT.
- FK a productos, sin borrado físico en cascada en MySQL.
- CHECK orden BETWEEN 1 AND 4 y UNIQUE(producto_id, orden): como máximo cuatro
  filas incluso ante escrituras concurrentes. Se admiten huecos (1,3,4).
- CHECK de URL HTTP/HTTPS; no admite data: Base64 ni binarios.

JPA: Producto tiene OneToMany LAZY, mappedBy=producto, orden por `orden`.
ProductoImagen tiene ManyToOne LAZY y es el propietario de la FK.
La futura operación debe asignar ambos lados (imagen.producto y colección).
PERSIST/MERGE en cascada y orphanRemoval permiten gestionar las filas de imagen
desde el producto. Quitar una imagen de la colección gestionada eliminará
esa fila; no afecta a imagenUrl ni al producto. No hay endpoint que lo haga hoy.
DELETE actual sigue siendo baja lógica y conserva todas las imágenes.

El índice UNIQUE no es diferible: al reordenar imágenes no intercambiar dos
posiciones mediante updates que colisionen temporalmente. La futura tarea
de imágenes deberá definir la operación transaccional de reemplazo/reordenación.
La colección se excluye de JSON; no cambia ProductoResponseDto ni se inicia
carga LAZY al construir las respuestas actuales. No hay subida MultipartFile.

## Verificación pendiente

- Aplicar 01 en una copia de la base, revisar columnas/FK/CHECK y arrancar
  con validate. Comparar productos existentes antes/después.
- Verificar que POST/PUT y movimientos funcionan en la fase transitoria.
- Tras la futura adaptación, probar rechazo de SKU duplicado/nulo, campos
  específicos ausentes y stockCritico negativo en la fase final.
- Verificar límites de cuatro imágenes, orden duplicado, FK inexistente y
  URLs data: rechazadas. Estas pruebas requieren otra autorización de escritura.
