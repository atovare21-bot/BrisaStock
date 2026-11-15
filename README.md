# BrisaStock

Sistema de gestión de inventarios para microempresas (Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security con HTTP Basic, MariaDB/MySQL e interfaz web HTML/CSS/JS servida por la propia aplicación).

## Requisitos
- Java 21
- MariaDB/MySQL con una base llamada `brisastock_db` (la aplicación crea/actualiza las tablas al arrancar, `ddl-auto=update`; **haga un respaldo antes de arrancar contra datos reales**).

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

## Swagger / OpenAPI
`/swagger-ui.html` y `/v3/api-docs` **requieren iniciar sesión como ADMIN** (el navegador pedirá usuario y contraseña). Para apagarlos: `SWAGGER_ENABLED=false`.

## Seguridad: lo que NO está configurado
- **No hay HTTPS/TLS configurado en la aplicación.** HTTP Basic envía el usuario y la contraseña en cada solicitud; úsela solo en una red local de confianza o detrás de un proxy con HTTPS.
- No hay límite de intentos de inicio de sesión.

## Calidad de datos de productos (Fase 6)
- **Fecha de vencimiento:** la API exige `AAAA-MM-DD` con fecha real de calendario (`2026-02-30` se rechaza con 400). Vacío = sin fecha. Los registros antiguos con otro formato **no se convierten ni se sobrescriben**: se tratan como "fecha desconocida" (no bloquean ventas, no cuentan como vencidos). Al editar un producto, reenviar la misma fecha antigua se tolera.
- **Vencido:** un producto se considera vencido si tiene el indicador `vencido`, el estado heredado `Vencido`, o una fecha válida **anterior a hoy** (campo de solo lectura `vencidoPorFecha`). Esos productos no se pueden vender (409).
- **Stock bajo:** una sola regla, `stock <= stockMinimo` de cada producto (`stockBajo` en el JSON, `/api/productos/stock-bajo`, `/api/reportes/stock-bajo`). La ruta antigua `/api/productos/stock-bajo/{n}` sigue existiendo pero **ignora el número**.
- **Agotado:** `stock <= 0` real (no el texto de `estado`). `/api/productos/estado/{estado}` sigue filtrando por el texto guardado.
- **Desactivación lógica:** `PATCH /api/productos/{id}/desactivar` y `/activar` (solo ADMIN). Un producto desactivado no se puede vender (409) y no aparece en alertas, reportes ni panel; sigue en el listado con `activo:false`, y sus ventas y movimientos se conservan (incluso se puede cancelar una venta suya). Requiere la columna nueva `productos.activo` (`boolean not null default true`): con `ddl-auto=update` se agrega sola y todos los productos existentes quedan activos. **Haga un respaldo antes de arrancar.**
- **PUT de producto:** `marca`, `categoria`, `descripcion`, `stockMinimo`, `vencido` y `fechaVencimiento` omitidos (null) **conservan** su valor; una cadena vacía `""` borra a propósito marca/categoría/descripción/fecha. El stock nunca cambia por PUT.
