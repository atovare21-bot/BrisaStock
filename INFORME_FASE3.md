# BrisaStock — Informe Fase 3 (Interfaz web)

## Estado de verificación (leer primero)
- **No pude ejecutar Maven en este entorno** (sin acceso a Maven Central). Por tanto NO se ejecutaron: las 85 pruebas previas, la prueba nueva `InterfazWebYSesionApiTest` (5 tests), ni la aplicación Spring real. Ejecute `.\mvnw test` en su equipo.
- La interfaz **sí se probó en Chromium real**, pero contra un **servidor simulado** que replica rutas, permisos y formato de error leídos del código Java. Eso valida la lógica y el diseño del frontend; **no valida la integración con Spring, Spring Security ni MariaDB**.
- Los archivos de la interfaz, `AuthController`, `SesionDTO`, el campo `stockBajo`, las reglas estáticas de `SecurityConfig` y `InterfazWebYSesionApiTest` ya estaban en el proyecto cuando empecé esta tarea (fechados hoy 17:19–17:23). No sé quién los creó. Los auditó y probé; no los reescribí.

## 1. Archivos
**Existentes (no creados por mí) — interfaz:** `static/index.html`, `static/css/app.css`, `static/js/{api,app,ui}.js`, `static/js/views/{panel,productos,ventas,inventario,reportes,usuarios}.js`.
**Existentes — backend:** `controller/AuthController`, `dto/SesionDTO`, `model/Producto` (campo JSON `stockBajo`, solo lectura), `config/SecurityConfig` (estáticos públicos y `/api/auth/me`), prueba `api/InterfazWebYSesionApiTest`.
**Modificados por mí:** `static/js/views/productos.js` (un cambio: el campo de fecha de vencimiento pasa a texto si el dato guardado no es AAAA-MM-DD; antes el editar habría guardado `null` y borrado ese dato).
**Nuevo:** este informe. (El simulador y la prueba de navegador están fuera del proyecto, en Node; el proyecto no incorpora Node.)

## 2. Estructura
HTML + CSS + JavaScript (módulos ES) servidos por Spring Boot desde `src/main/resources/static`; sin frameworks ni build. Flujo: Interfaz → API REST → Controller → Service → Repository → BD. Todo contenido dinámico se inserta con `textContent` (probado con un nombre de producto con HTML: no se ejecuta).

## 3 y 4. Pantallas y endpoints
| Pantalla | Endpoints | Roles |
|---|---|---|
| Login | GET /api/auth/me | todos |
| Panel | GET /api/productos; GET /api/ventas | ADMIN y USER (ventas: ADMIN) |
| Productos | GET /api/productos, /buscar, /{id}; POST, PUT, DELETE /api/productos; GET /api/inventario/productos/{id}/movimientos | lectura ADMIN/USER; escritura y movimientos ADMIN |
| Ventas | POST /api/ventas; GET /api/ventas; POST /api/ventas/{id}/cancelar | registrar ADMIN/USER; historial y cancelar ADMIN |
| Inventario | POST /api/inventario/entradas, /salidas, /ajustes; GET /api/inventario/movimientos y /productos/{id}/movimientos | entradas ADMIN/USER; resto ADMIN |
| Reportes | GET /api/reportes/stock-bajo, /agotados, /vencidos | ADMIN |
| Usuarios | GET /api/usuarios; POST /api/usuarios/admin | ADMIN |

## 5. Cómo iniciar y acceder
1. Haga un respaldo de `brisastock_db` (mysqldump). Con `ddl-auto=update` la app agrega la tabla y columnas de Fase 2 al arrancar.
2. MariaDB/MySQL en `localhost:3306`, base `brisastock_db` (según `application.properties`).
3. `.\mvnw spring-boot:run` y abra **http://localhost:9090/** (puerto de `server.port`). Swagger: `/swagger-ui.html` (no lo verifiqué: la app no corrió aquí).
4. Inicie sesión con un usuario existente (contraseña BCrypt). Use un ADMIN existente. (Desde la Fase 5 ya no hay registro público: solo un ADMIN crea usuarios; ver README.md.)

## 8. Pruebas realmente ejecutadas
- Chromium + servidor simulado: **39 verificaciones, 39 correctas, 0 fallas** (login correcto/incorrecto, menús por rol, redirección de ruta no permitida, panel, búsqueda/filtros, crear/editar/eliminar con errores 400/404/409 mostrados, PUT sin stock, venta rechazada y aceptada, cancelación con motivo, entradas/salidas/ajustes con confirmación, historial, reportes, usuarios, XSS, móvil 390 px sin scroll horizontal, error 500 sin detalles internos, sin errores de JS, credenciales no guardadas en storage/cookies). Algunas verificaciones son débiles: solo confirman que la pantalla llegó al estado esperado, no validan su contenido.
- Sintaxis: los 9 módulos JS pasan `node --check`; el Java no tiene errores de sintaxis (javac sin dependencias).
- **Maven / 85 pruebas / Spring / MariaDB: no ejecutado.**

## 9. Problemas pendientes y limitaciones
- Sin validar contra el backend real ni contra MariaDB.
- Las credenciales viven solo en memoria: al recargar la página hay que iniciar sesión otra vez (decisión de seguridad; guardarlas en el navegador sería peor con HTTP Basic).
- **HTTP Basic envía usuario y contraseña en cada solicitud: en producción debe usarse HTTPS.**
- Pendientes de seguridad heredados: `@CrossOrigin("*")` en varios controladores y CSRF desactivado (aceptable con Basic sin cookies); no hay Content-Security-Policy.
- Sin paginación: Productos carga todo el listado; el historial de ventas no tiene límite; movimientos sí (límite 50/200/1000).
- El backend no ofrece editar usuarios ni roles, ni un endpoint de resumen de ventas; la interfaz no lo simula.
- "Ventas recientes" del panel solo aparece para ADMIN (GET /api/ventas es ADMIN).

## 10. Decisiones que requieren su aprobación
1. Confirmar que `GET /api/auth/me` y el campo `stockBajo` (cambios mínimos al backend) son aceptables.
2. Mantener o no el criterio "entradas: ADMIN y USER; salidas, ajustes e historial: ADMIN".
3. Origen de los archivos de interfaz ya presentes: confirmar que son los que usted espera.
