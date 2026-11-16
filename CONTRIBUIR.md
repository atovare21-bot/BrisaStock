# Cómo contribuir a BrisaStock

Guía para los integrantes del equipo. Sigue los pasos en orden; los comandos se escriben en la terminal de Visual Studio Code (menú **Terminal → Nuevo terminal**).

## 1. Una sola vez

1. **Acepta la invitación.** GitHub te envió un correo ("…invited you to collaborate"). También aparece en <https://github.com/atovare21-bot/BrisaStock/invitations>. Sin aceptar no podrás subir cambios.
2. **Instala** [Git](https://git-scm.com/download/win), Java 21 y Visual Studio Code.
3. **Identifícate en Git con el mismo correo de tu cuenta de GitHub.** Así tus commits aparecen con tu usuario y tu foto:
   ```powershell
   git config --global user.name "Tu Nombre"
   git config --global user.email "el-correo-de-tu-cuenta-de-github"
   ```
4. **Clona el proyecto:**
   ```powershell
   git clone https://github.com/atovare21-bot/BrisaStock.git
   cd BrisaStock
   ```
   La primera vez que subas algo, Git abrirá el navegador para que inicies sesión en GitHub.

## 2. Cada vez que vayas a trabajar

```powershell
git checkout main
git pull                                  # trae lo último del equipo
git checkout -b nombre-de-tu-cambio       # tu rama, por ejemplo: reporte-ventas-diarias
```

Haz tu cambio y comprueba que no dañaste nada:

```powershell
.\mvnw.cmd test                           # deben pasar todas las pruebas
```

Guarda y sube:

```powershell
git status                                # revisa qué archivos cambiaste
git add .
git commit -m "Describe el cambio en una frase"
git push -u origin nombre-de-tu-cambio
```

Después entra al repositorio en GitHub, pulsa **Compare & pull request**, explica qué hiciste y pide a un compañero que lo revise. Cuando lo apruebe, pulsa **Merge pull request**.

## 3. Reglas del equipo

- Un cambio por rama y por pull request; mensajes de commit claros y en español.
- No se sube directo a `main` sin que las pruebas pasen.
- **Nunca** subas contraseñas, respaldos de la base de datos (`.sql` con datos) ni la carpeta `target/`.
- Si cambias una tabla o una entidad, actualiza `db/esquema.sql` y explica el cambio en el pull request.
- Si Git dice que hay un conflicto, no fuerces nada (`--force`): avisa en el grupo y lo resolvemos juntos.

## 4. Cómo queda la evidencia del trabajo de cada uno

GitHub registra automáticamente quién hizo cada cosa:

- **Commits:** <https://github.com/atovare21-bot/BrisaStock/commits/main> muestra autor, fecha y mensaje de cada cambio.
- **Aportes por persona:** pestaña **Insights → Contributors**.
- **Pull requests:** pestaña **Pull requests → Closed** muestra quién propuso y quién revisó cada cambio.

Para que tu aporte cuente a tu nombre, hazlo tú desde tu computador y con tu cuenta. Un commit hecho por otra persona aparece a nombre de esa persona.

## 5. Tareas abiertas para repartir

Cada una es pequeña e independiente, así nadie se pisa con nadie. Antes de empezar una, avisa en el grupo cuál tomas.

| Tarea | Archivos principales |
|---|---|
| Paginación en el listado de productos y en el historial de ventas | `ProductoController`, `VentaController`, `static/js/views` |
| Pantalla para desactivar usuarios y cambiar contraseña | `UsuarioController`, `UsuarioService`, `static/js/views/usuarios.js` |
| Reporte de ventas por día y por vendedor (ya existe `ventas.usuario_id`) | `ReporteController`, `ReporteService`, `static/js/views/reportes.js` |
| Lista de categorías para elegir al crear un producto | nuevo `CategoriaController`, `static/js/views/productos.js` |
| Capturas y manual de usuario de la aplicación | carpeta `docs/` (nueva) |

## 6. Ejecutar el proyecto

Ver [README.md](README.md): requisitos, base de datos, variables de entorno y pruebas.
