# Etapa 1: retiro controlado de módulos

## Decisión

- Retirados: `resena-service` y `soporte-service`, incluidas sus fuentes,
  pruebas heredadas, Maven Wrapper, POM, Dockerfile y configuración local.
- Conservado temporalmente: `promocion-service`.
- Se mantienen diez módulos. No se fusionan ni renombran servicios.
- No se modifica código Java de los módulos conservados, Spring Security,
  JWT, entidades, tablas, dependencias ni versiones.

## Dependencias verificadas antes del retiro

| Origen | Destino | Uso |
| --- | --- | --- |
| Ventas | Promociones | GET `/api/promociones/validar/{codigo}` cuando hay cupón |
| Reseñas | Usuarios | Validar usuario |
| Reseñas | Catálogo | Validar producto |
| Reseñas | Ventas | Verificar compra previa |
| Soporte | Usuarios | Validar usuario |
| Soporte | Pedidos | Consultar pedido y verificar propietario |

No se encontraron consumidores Java de Reseñas o Soporte entre los servicios
conservados, ni imports o dependencias Maven hacia sus clases. Los POM son
independientes y no existe un agregador raíz que requiera editar módulos.

Las restantes relaciones WebClient se conservan: Auth -> Usuarios;
Catálogo <-> Inventario; Ventas -> Usuarios, Inventario y Pedidos;
Pedidos <-> Despacho.

### Bloqueo de Promociones

`venta-service/src/main/java/com/ferreteria/venta_service/service/VentaService.java`
consulta Promociones y aplica un descuento porcentual antes de calcular el total.
Si se recibe un cupón y Promociones no responde, la venta falla. El contrato
`VentaRequestDto.codigoPromocion`, el controlador y las pruebas existentes también
conservan este flujo. No basta con eliminar una ruta o el módulo.

Durante la migración de Ventas habrá que retirar el contrato y cálculo de cupones
y obtener del Catálogo el precio efectivo conforme a `enOferta`, `precio` y
`precioOferta`. Solo entonces se podrá retirar Promociones y sus referencias.
No se implementa esa sustitución ni se cambia el cálculo heredado de IVA aquí.

## Referencias actualizadas

- Ejemplo del Gateway: retiradas rutas `lb://resena-service` y
  `lb://soporte-service`, sus predicados y entradas Swagger.
- Scripts `build-all.bat`, `clean-all.bat` e `iniciar_servicios.bat`: retirados
  exclusivamente los bloques de los dos módulos eliminados.
- README: inventario y rutas actuales, dependencia temporal de Promociones
  y aclaración de que no existe Compose ni un despliegue operativo.
- COPIA_BASE: identificado como registro histórico con el estado actual.

Eureka usa registro dinámico y no tiene una lista estática de estos módulos.
No se inicia ni se modifica ningún registro Eureka en ejecución.
No existe Compose activo que actualizar y no se reconstruye el archivo omitido.

## Referencias residuales deliberadas

- `api-gateway/.../config/SecurityConfig.java` conserva los matchers de
  `/api/resenas/**` y `/api/soporte/**`: la tarea prohíbe modificar Spring Security.
  Son reglas de autorización, no rutas hacia servicios ni imports rotos.
  Su limpieza queda pendiente de una tarea autorizada de seguridad.
- Un comentario de `VentaControllerTest.java` menciona `SoporteControllerTest`;
  no es una dependencia de compilación. Se conserva el Java restante intacto.
- La documentación histórica y este registro mencionan los módulos retirados
  para explicar el cambio, no como servicios disponibles.

## Límites y pendientes

- Auth y Gateway siguen sin configuración activa; solo existen sus ejemplos.
- Las siete configuraciones MySQL restantes son locales e ignoradas por Git;
  deben reemplazarse por configuración propia antes de ejecutar.
- El descubrimiento dinámico del Gateway sigue habilitado en su ejemplo.
  En un futuro entorno compartido debe evitarse registrar instancias antiguas
  de los módulos retirados; eliminar rutas explícitas no aísla ese entorno.
- `build-all.bat` aún contiene limpieza global de Docker. No se ejecuta ni se
  rediseña ese comportamiento en esta tarea.
- No se valida funcionamiento integral, ni se inicia Spring, Docker o MySQL.
- Las fusiones de Identidad, Catálogo y Ventas siguen pendientes.

## Verificación realizada

- Diez módulos con POM XML legible, fuentes principales, Dockerfile y Maven
  Wrapper completos.
- Sin imports, dependencias Maven, llamadas WebClient ni referencias en scripts
  o configuraciones fuente hacia los dos servicios retirados.
- Sin cambios en Java ni POM de los módulos conservados, comprobado con Git.
- `git diff --check` sin errores.
- Compilación individual de `venta-service` y `api-gateway` con
  `mvnw.cmd -o -B -Dmaven.test.skip=true compile`: ambas con `BUILD SUCCESS`.
  Solo se ejecutó la fase de compilación offline, sin pruebas ni arranque.
- Se retiraron también los artefactos generados que quedaban dentro de los
  directorios eliminados (21 archivos de Reseñas y 20 de Soporte).
- No se compilaron los otros ocho módulos ni se ejecutaron pruebas de integración.
  No se verificó arranque: faltan las configuraciones activas de Auth/Gateway y,
  para un despliegue con Docker, Compose. Estos faltantes no impidieron compilar
  los dos módulos comprobados.
- No se crearon commits, no se cambió de rama y no se hizo push.
