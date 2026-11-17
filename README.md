# BrisaStock

## Reporte de ventas por día y vendedor

La pantalla de reportes permite consultar ventas activas agrupadas por fecha y vendedor. El filtro `desde`/`hasta` es opcional y ambas fechas son inclusivas.

La API expone `GET /api/reportes/ventas-por-dia`; las fechas usan el formato `AAAA-MM-DD`:

```text
GET /api/reportes/ventas-por-dia?desde=2026-01-01&hasta=2026-01-31
```

Cada fila incluye fecha, nombre de usuario del vendedor, cantidad de ventas y total. Las ventas canceladas se excluyen.

Sistema de gestión de inventarios para microempresas (Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security con HTTP Basic, MariaDB/MySQL e interfaz web HTML/CSS/JS servida por la propia aplicación).

## Requisitos
- Java 21
- MariaDB/MySQL con una base llamada `brisastock_db` (la aplicación crea/actualiza las tablas al arrancar, `ddl-auto=update`; **haga un respaldo antes de arrancar contra datos reales**).

## Base de datos (modelo v2)
Seis tablas relacionadas. La estructura completa está en [`db/esquema.sql`](db/esquema.sql).

| Tabla | Qué guarda | Se relaciona con |
|---|---|---|
| `usuarios` | Personas que pueden entrar y su rol | registra `ventas`, cancela `ventas`, hace `movimientos_inventario` |
| `categorias` | Categorías de productos (una fila por categoría) | tiene muchos `productos` |
| `productos` | Catálogo, precios, existencias y vencimiento | pertenece a una `categoria` |
| `ventas` | Cada venta, quién la registró y, si aplica, quién la canceló | la registra un `usuario` |
| `detalle_ventas` | Líneas de cada venta: cantidad y precio del momento | une `ventas` con `productos` |
| `movimientos_inventario` | Historial de todo cambio de stock | de un `producto`, hecho por un `usuario` |

- El dinero se guarda como `DECIMAL(12,2)` y la fecha de vencimiento como `DATE`.
- `detalle_ventas` solo guarda lo que no se puede obtener de otra tabla: la cantidad y el precio unitario del momento. El nombre del producto se lee del producto, el subtotal se calcula y el stock antes/después está en `movimientos_inventario`.
- En el JSON de la API los usuarios relacionados se publican solo como su `username` y la categoría como su nombre.
- **Instalación nueva:** la aplicación crea las tablas al arrancar (`ddl-auto=update`).
- **Base con el modelo anterior (5 tablas):** haga un respaldo y aplique una sola vez [`db/migracion_v2.sql`](db/migracion_v2.sql); el archivo explica cada paso. No borra productos, ventas, movimientos ni usuarios.

## Configuración por variables de entorno
Todas son opcionales. Si no define ninguna, la aplicación usa la configuración local de desarrollo de siempre (`localhost:3306`, base `brisastock_db`, usuario `root`, contraseña vacía).

| Variable | Para qué sirve | Valor por defecto |
|---|---|---|
| `DB_URL` | URL JDBC de la base de datos | `jdbc:mysql://localhost:3306/brisastock_db` |
| `DB_USERNAME` | Usuario de la base de datos | `root` |
| `DB_PASSWORD` | Contraseña de la base de datos | (vacía) |
| `SHOW_SQL` | `true` imprime las consultas SQL en consola (solo para depurar) | `false` |
| `SWAGGER_ENABLED` | `false` apaga por completo Swagger/OpenAPI | `true` (solo accesible para un ADMIN) |
| `JPA_DDL_AUTO` | Modo de esquema de Hibernate (`update`, `validate`, `none`) | `update` |

### Windows PowerShell
Las variables definidas así valen **solo para esa ventana de PowerShell**:

```powershell
$env:DB_USERNAME = "brisastock_app"
$env:DB_PASSWORD = "la-contraseña-de-ese-usuario"
# Solo si la base no está en localhost:3306 o se llama distinto:
# $env:DB_URL = "jdbc:mysql://localhost:3306/brisastock_db"
# Solo para depurar consultas:
# $env:SHOW_SQL = "true"

.\mvnw.cmd spring-boot:run
```

Para verificar que quedaron definidas (sin imprimir la contraseña): `Get-ChildItem Env:DB_USERNAME`.
Para quitarlas en esa ventana: `Remove-Item Env:DB_PASSWORD`.
Si las quiere permanentes para su usuario de Windows: `setx DB_USERNAME "brisastock_app"` (aplica a ventanas **nuevas**). No escriba contraseñas en archivos que vaya a subir a un repositorio.

> Recomendado: no usar `root` para la aplicación. Cree un usuario de base de datos propio con permisos solo sobre `brisastock_db`.

## Ejecutar
```powershell
.\mvnw.cmd spring-boot:run
```
Interfaz: <http://localhost:9090/>

## Pruebas
```powershell
.\mvnw.cmd test
```
Las pruebas usan una base H2 en memoria (con una guardia que aborta si no es H2); **no leen `DB_*` ni se conectan a `brisastock_db`**.

## Usuarios y permisos
- **No hay registro público.** Solo un `ADMIN` autenticado puede crear usuarios: desde la pantalla *Usuarios* de la interfaz o con `POST /api/usuarios` (siempre crea rol `USER`) y `POST /api/usuarios/admin` (permite elegir `ADMIN` o `USER`).
- Roles: `ADMIN` y `USER`. Los usuarios antiguos con rol `EMPLEADO` se tratan como `USER`.
- La aplicación no trae un administrador inicial: el primer `ADMIN` debe existir ya en la tabla `usuarios` (los usuarios actuales no se modifican).
- No hay (todavía) pantalla para desactivar usuarios ni cambiar contraseñas.

## Accesibilidad y búsqueda rápida
Pensadas para que cualquier persona pueda usar la aplicación sin enredarse. No cambian datos ni permisos: son solo de presentación y viven en el navegador.

- **Botón "Accesibilidad"** (barra superior y pantalla de inicio de sesión):
  - Tamaño de la letra: Normal, Grande, Muy grande o Máximo. Agranda también botones y campos; con letra amplia las tablas pasan a tarjetas para no tener que desplazarse hacia los lados.
  - Alto contraste: texto más oscuro y bordes más marcados.
  - Botones y campos más grandes: más fáciles de tocar.
  - Reducir animaciones.
- Las preferencias se guardan en `localStorage` de ese dispositivo (clave `brisastock.accesibilidad`). **Nunca** se guardan allí credenciales ni datos del negocio.
- **Búsqueda rápida**: botón "Buscar", `Ctrl + K` o la tecla `/`. Un solo cuadro para ir a una sección o encontrar un producto por nombre, marca, categoría o ID. Ignora tildes y mayúsculas ("atun" encuentra "Atún"). Con las flechas se recorren los resultados y con Enter se abre el elegido. Solo ofrece las secciones que el rol puede ver.
- Los formularios marcan los campos con error con `aria-invalid` y enlazan el mensaje, para lectores de pantalla.

Archivos: `static/js/accesibilidad.js`, `static/js/busqueda.js` y el bloque "Accesibilidad y búsqueda rápida" de `static/css/app.css`.

## Trabajo en equipo con Git
Repositorio: <https://github.com/atovare21-bot/BrisaStock>. La guía paso a paso para cada integrante está en [`CONTRIBUIR.md`](CONTRIBUIR.md).

No suba contraseñas, respaldos de la base de datos ni la carpeta `target/`.

## Swagger / OpenAPI
`/swagger-ui.html` y `/v3/api-docs` **requieren iniciar sesión como ADMIN** (el navegador pedirá usuario y contraseña). Para apagarlos: `SWAGGER_ENABLED=false`.

## Seguridad: lo que NO está configurado
- **No hay HTTPS/TLS configurado en la aplicación.** HTTP Basic envía el usuario y la contraseña en cada solicitud; úsela solo en una red local de confianza o detrás de un proxy con HTTPS.
- No hay límite de intentos de inicio de sesión.

## Calidad de datos de productos (Fase 6)
- **Fecha de vencimiento:** la API exige `AAAA-MM-DD` con fecha real de calendario (`2026-02-30` se rechaza con 400). Vacío = sin fecha. En la base es una columna `DATE`, así que no puede guardarse un texto que no sea una fecha.
- **Vencido:** un producto se considera vencido si tiene la marca manual `vencido` o una fecha **anterior a hoy** (campo de solo lectura `vencidoPorFecha`). Esos productos no se pueden vender (409).
- **Estado:** `Disponible`, `Agotado` o `Vencido` ya no es una columna; se calcula a partir del stock y de la marca de vencido, y se publica en el JSON como `estado`.
- **Stock bajo:** una sola regla, `stock <= stockMinimo` de cada producto (`stockBajo` en el JSON, `/api/productos/stock-bajo`, `/api/reportes/stock-bajo`). La ruta antigua `/api/productos/stock-bajo/{n}` sigue existiendo pero **ignora el número**.
- **Agotado:** `stock <= 0`. `/api/productos/estado/{estado}` filtra por el estado calculado.
- **Desactivación lógica:** `PATCH /api/productos/{id}/desactivar` y `/activar` (solo ADMIN). Un producto desactivado no se puede vender (409) y no aparece en alertas, reportes ni panel; sigue en el listado con `activo:false`, y sus ventas y movimientos se conservan (incluso se puede cancelar una venta suya).
- **PUT de producto:** `marca`, `categoria`, `descripcion`, `stockMinimo`, `vencido` y `fechaVencimiento` omitidos (null) **conservan** su valor; una cadena vacía `""` borra a propósito marca/categoría/descripción/fecha. El stock nunca cambia por PUT.
