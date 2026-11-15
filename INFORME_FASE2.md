# BrisaStock — Informe técnico Fase 2 (Integridad y trazabilidad del inventario)

> **ESTADO DE VERIFICACIÓN: el código y las pruebas de Fase 2 NO fueron compilados ni ejecutados.**
> El entorno donde se escribió no tiene acceso a Maven Central (el proxy respondió 403 a
> `repo.maven.apache.org`), así que `mvnw test` no pudo correr. Solo se verificó que no hay
> errores de sintaxis Java (javac sin dependencias). Todo lo demás debe validarse ejecutando
> `.\mvnw test` en su máquina. Ningún resultado de este informe es un resultado de pruebas.

## A. Archivos modificados
Producción (10): `model/Producto`, `model/Venta`, `repository/ProductoRepository`, `service/ProductoService`,
`service/VentaService`, `service/ReporteService`, `controller/ProductoController`, `controller/VentaController`,
`config/SecurityConfig`, `exception/GlobalExceptionHandler`.
Pruebas (3): `support/PruebaApiBase`, `service/VentaServiceTest`, `api/ProductoApiTest`.

## B. Archivos nuevos
Producción (12): `model/TipoMovimiento`, `model/EstadoVenta`, `model/MovimientoInventario`,
`repository/MovimientoInventarioRepository`, `service/InventarioService`, `service/UsuarioActual`,
`controller/InventarioController`, `dto/MovimientoStockRequestDTO`, `dto/AjusteStockRequestDTO`,
`dto/CancelarVentaRequestDTO`, `dto/MovimientoInventarioDTO`, `dto/ProductoActualizacionDTO`.
Pruebas (6 clases, 38 tests): `model/ProductoStockMinimoTest` (5), `api/InventarioApiTest` (20),
`api/VentaInventarioApiTest` (8), `api/ConcurrenciaInventarioTest` (2),
`api/ConflictoConcurrenciaHttpTest` (1), `api/RollbackMovimientosTest` (2).

## C. Cambios realizados
- **Movimientos**: entidad `MovimientoInventario` (producto, tipo, cantidad, stock anterior/posterior, fecha, usuario, referencia, observación). Tipos: ENTRADA, SALIDA, VENTA, AJUSTE, DEVOLUCION.
- **InventarioService**: entradas, salidas, ajustes (transaccionales; stock nunca negativo; actualiza `estado`).
- **PUT /api/productos/{id}**: ya no modifica stock. Usa `ProductoActualizacionDTO`; si trae `stock` distinto del actual → 400 indicando usar /api/inventario. Si no trae `stock` o es igual, se ignora.
- **POST /api/productos** con stock > 0 registra un movimiento ENTRADA (ref. `STOCK_INICIAL`).
- **Ventas**: cada línea genera un movimiento VENTA (ref. `VENTA #id`). Nuevo `POST /api/ventas/{id}/cancelar`.
- **Cancelación**: `Venta.estado` ACTIVA/CANCELADA (+ fecha, usuario y motivo). No se borra nada; devuelve stock con movimientos DEVOLUCION; segunda cancelación → 409.
- **stockMinimo**: regla única `stock <= stockMinimo` (nulo = 0) en `Producto.esStockBajo()` y en `ProductoRepository.findConStockBajo()`; `/api/reportes/stock-bajo` ya no usa el 5 fijo.
- **Concurrencia**: `@Version` en `Producto`; `OptimisticLockingFailureException` → 409 (formato ApiError).
- **Eliminar producto** con movimientos registrados → 409 (para no perder trazabilidad).
- **Usuario responsable**: se toma del usuario autenticado; si no hay (llamadas internas) se registra `sistema`.

## D. Modelo de datos
```
Producto 1 ───── N MovimientoInventario   (movimientos_inventario.producto_id → productos.id)
Venta    1 ───── N DetalleVenta ── N:1 Producto   (sin cambios)
```
`MovimientoInventario.usuario` es texto (username), sin FK a usuarios.

## E. Endpoints
| Método | Endpoint | Función | Rol |
|---|---|---|---|
| POST | /api/inventario/entradas | Sumar stock | ADMIN, USER |
| POST | /api/inventario/salidas | Restar stock | ADMIN |
| POST | /api/inventario/ajustes | Fijar stock (observación obligatoria) | ADMIN |
| GET | /api/inventario/movimientos?limite= | Historial general (def. 200, máx. 1000) | ADMIN |
| GET | /api/inventario/productos/{id}/movimientos | Historial de un producto | ADMIN |
| POST | /api/ventas/{id}/cancelar | Cancelar/devolver venta (cuerpo opcional `{"motivo"}`) | ADMIN |
| PUT | /api/productos/{id} | MODIFICADO: sin cambio de stock | ADMIN |
| GET | /api/reportes/stock-bajo | MODIFICADO: usa stockMinimo por producto | ADMIN |
| POST | /api/ventas | MODIFICADO: genera movimientos VENTA | ADMIN, USER |

## F. Reglas de negocio
- **Stock bajo**: `stock <= stockMinimo`; stockMinimo nulo cuenta como 0. (`GET /api/productos/stock-bajo/{n}` conserva su semántica explícita "stock < n" con el umbral que envía el cliente.)
- **Stock negativo**: imposible; salida/venta mayor al stock → 409; ajuste negativo → 400.
- **Entrada/Salida**: cantidad > 0. **Ajuste**: nuevo stock >= 0, distinto del actual, con observación; `cantidad` = diferencia absoluta.
- **Venta**: descuenta stock + movimiento VENTA, todo en una transacción. **Devolución**: suma stock + movimiento DEVOLUCION; una sola vez por venta.
- **Concurrencia**: optimista (`@Version`); el perdedor recibe 409 y su transacción se revierte completa; el cliente reintenta. Una cancelación doble simultánea también queda cubierta porque ambas modifican los mismos productos.

## G. Pruebas
Tests antes de Fase 2: 47 (reportados por usted; no pude re-ejecutarlos aquí).
Tests después de Fase 2: **NO EJECUTADOS**. Por conteo estático: 47 existentes + 38 nuevos = 85.
Exitosos / Fallidos / Errores / Omitidos: **sin datos (no se pudo ejecutar Maven)**.
Cambios a pruebas existentes: (1) `ProductoApiTest.actualizar_valido_modificaDatos_yTodaviaPermiteCambiarStock` fue reescrito porque exigía justo lo que la Fase 2 prohíbe; (2) `PruebaApiBase` limpia también `movimientos_inventario`; (3) `VentaServiceTest` añade un `@Mock InventarioService`.

## H. Base de datos
- Cambios de esquema (vía `ddl-auto=update`, solo aditivos): tabla nueva `movimientos_inventario`; columna nueva `productos.version` (`bigint not null default 0`); columnas nuevas en `ventas`: `estado` (`varchar(20) not null default 'ACTIVA'`), `fecha_cancelacion`, `usuario_cancelacion`, `motivo_cancelacion`.
- Ninguna tabla/columna existente se renombra ni elimina. No hay DROP/TRUNCATE/DELETE.
- La BD real NO se usó: las pruebas usan H2 en memoria (con guardia `SoloBdAislada`). **La compatibilidad con MariaDB real no está validada**; en especial que `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT` se aplique bien sobre los datos existentes. Haga un respaldo (mysqldump) antes de arrancar la app por primera vez contra `brisastock_db`.

## I. Riesgos pendientes
- Nada de esto está compilado/ejecutado todavía.
- Sin migraciones versionadas (Flyway/Liquibase quedó fuera de alcance): el esquema depende de `ddl-auto=update`.
- Productos existentes sin movimientos: su stock actual no tiene historial previo (el historial empieza con Fase 2).
- Los movimientos no son inmutables a nivel de BD (solo no hay endpoints para editarlos/borrarlos).
- Historial con límite simple, sin paginación real.
- `GET /api/productos/stock-bajo/{n}` usa un umbral del cliente (por compatibilidad), distinto de la regla por producto.
- Bajo alta concurrencia el optimista devuelve 409 y el cliente debe reintentar (sin reintento automático).
- Pruebas de concurrencia con hilos sobre H2: su comportamiento sobre MariaDB puede diferir.
