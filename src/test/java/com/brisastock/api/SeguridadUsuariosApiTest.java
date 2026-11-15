package com.brisastock.api;

import com.brisastock.model.Usuario;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Autenticación, autorización por roles y gestión de usuarios (solo ADMIN crea usuarios). */
class SeguridadUsuariosApiTest extends PruebaApiBase {

    private static final String REGISTRO = """
            {"nombre":"Ana","username":"ana.perez","password":"ClaveSegura1"}""";

    @Test
    void sinCredenciales_devuelve401ConFormatoEstandar() throws Exception {
        mvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void contrasenaIncorrecta_devuelve401() throws Exception {
        mvc.perform(get("/api/productos").with(httpBasic("admin", "incorrecta")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioInactivo_noPuedeAutenticarse() throws Exception {
        mvc.perform(get("/api/productos").with(httpBasic("inactivo", "Inactivo123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void empleadoConRolLegado_seTrataComoUser() throws Exception {
        mvc.perform(get("/api/productos").with(httpBasic("empleado1", "Empleado123")))
                .andExpect(status().isOk());
    }

    @Test
    void user_noPuedeCrearActualizarNiEliminarProductos() throws Exception {
        String cuerpo = """
                {"nombre":"X","precioCompra":1,"precioVenta":2,"stock":1}""";
        mvc.perform(post("/api/productos").with(httpBasic("cajero", "Cajero12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void user_noPuedeVerHistorialDeVentasNiReportes_peroAdminSi() throws Exception {
        mvc.perform(get("/api/ventas").with(httpBasic("cajero", "Cajero12345")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/stock-bajo").with(httpBasic("cajero", "Cajero12345")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ventas").with(httpBasic("admin", "Admin12345")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/stock-bajo").with(httpBasic("admin", "Admin12345")))
                .andExpect(status().isOk());
    }

    @Test
    void user_puedeRegistrarVentas() throws Exception {
        var p = crearProducto("Cola", 5, 1000.0, false);
        String venta = "{\"productos\":[{\"productoId\":" + p.getId() + ",\"cantidad\":2}]}";
        mvc.perform(post("/api/ventas").with(httpBasic("cajero", "Cajero12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(venta))
                .andExpect(status().isOk());
        assertEquals(3, stockActual(p.getId()));
    }

    // ------------------------------------------------------------------
    // Fase 5: ya NO existe registro público. Crear usuarios requiere ADMIN.
    // (Las pruebas anteriores de "registro público" se reemplazan por estas,
    // que verifican la política nueva: anónimo 401, USER 403, ADMIN 201.)
    // ------------------------------------------------------------------

    private static final String ADMIN_NUEVO = """
            {"nombre":"X","username":"xyz","password":"ClaveSegura1","rol":"ADMIN"}""";

    @Test
    void anonimo_noPuedeCrearUsuarios_401_yNoSeCreaNada() throws Exception {
        long antes = usuarioRepository.count();

        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(REGISTRO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        mvc.perform(post("/api/usuarios/admin").contentType(MediaType.APPLICATION_JSON).content(ADMIN_NUEVO))
                .andExpect(status().isUnauthorized());

        assertEquals(antes, usuarioRepository.count());
        assertTrue(usuarioRepository.findByUsername("ana.perez").isEmpty());
    }

    @Test
    void user_noPuedeCrearUsuarios_403_yNoSeCreaNada() throws Exception {
        long antes = usuarioRepository.count();

        mvc.perform(post("/api/usuarios").with(httpBasic("cajero", "Cajero12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTRO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mvc.perform(post("/api/usuarios/admin").with(httpBasic("cajero", "Cajero12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(ADMIN_NUEVO))
                .andExpect(status().isForbidden());
        // el empleado con rol heredado "EMPLEADO" tampoco
        mvc.perform(post("/api/usuarios/admin").with(httpBasic("empleado1", "Empleado123"))
                        .contentType(MediaType.APPLICATION_JSON).content(ADMIN_NUEVO))
                .andExpect(status().isForbidden());

        assertEquals(antes, usuarioRepository.count());
        assertTrue(usuarioRepository.findByUsername("xyz").isEmpty());
    }

    @Test
    void user_noPuedeAsignarseElRolAdmin() throws Exception {
        // Intento de auto-promoción: crear un ADMIN, o reenviar su propio username con rol ADMIN
        String autoPromocion = """
                {"nombre":"cajero","username":"cajero","password":"ClaveSegura1","rol":"ADMIN"}""";
        mvc.perform(post("/api/usuarios/admin").with(httpBasic("cajero", "Cajero12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(autoPromocion))
                .andExpect(status().isForbidden());

        Usuario cajero = usuarioRepository.findByUsername("cajero").orElseThrow();
        assertEquals("USER", cajero.getRol());
        assertTrue(passwordEncoder.matches("Cajero12345", cajero.getPassword()),
                "la contraseña del usuario no debe cambiar");
        // y sigue sin poder usar funciones de administrador
        mvc.perform(get("/api/usuarios").with(httpBasic("cajero", "Cajero12345")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_creaUsuarioBasico_conRolUser_yNuncaExponeLaContrasena() throws Exception {
        MvcResult res = mvc.perform(post("/api/usuarios").with(httpBasic("admin", "Admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTRO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("USER"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        String cuerpo = res.getResponse().getContentAsString();
        assertFalse(cuerpo.contains("$2"), "la respuesta no debe contener un hash BCrypt");

        Usuario guardado = usuarioRepository.findByUsername("ana.perez").orElseThrow();
        assertTrue(guardado.getPassword().startsWith("$2"));
        assertTrue(passwordEncoder.matches("ClaveSegura1", guardado.getPassword()));
        assertEquals("USER", guardado.getRol());
    }

    @Test
    void admin_creaUsuarioBasico_ignoraRolYActivoEnviadosEnElJson() throws Exception {
        String cuerpo = """
                {"nombre":"M","username":"mallory","password":"ClaveSegura1",
                 "rol":"ADMIN","activo":false,"id":999}""";
        mvc.perform(post("/api/usuarios").with(httpBasic("admin", "Admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("USER"))
                .andExpect(jsonPath("$.activo").value(true));

        Usuario u = usuarioRepository.findByUsername("mallory").orElseThrow();
        assertEquals("USER", u.getRol());
        assertTrue(u.getActivo());
    }

    @Test
    void usuarioCreadoPorAdmin_iniciaSesion_peroNoGestionaUsuarios() throws Exception {
        mvc.perform(post("/api/usuarios").with(httpBasic("admin", "Admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTRO))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/auth/me").with(httpBasic("ana.perez", "ClaveSegura1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("USER"));
        mvc.perform(post("/api/usuarios/admin").with(httpBasic("ana.perez", "ClaveSegura1"))
                        .contentType(MediaType.APPLICATION_JSON).content(ADMIN_NUEVO))
                .andExpect(status().isForbidden());
    }

    @Test
    void usernameDuplicado_devuelve409() throws Exception {
        mvc.perform(post("/api/usuarios").with(httpBasic("admin", "Admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTRO))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/usuarios").with(httpBasic("admin", "Admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTRO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void datosInvalidos_devuelven400ConErroresPorCampo() throws Exception {
        String cuerpo = """
                {"nombre":"","username":"a!","password":"corta"}""";
        mvc.perform(post("/api/usuarios").with(httpBasic("admin", "Admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errores").isArray());
    }

    // ------------------------- Swagger / OpenAPI -------------------------

    private int estado(String ruta, org.springframework.test.web.servlet.request.RequestPostProcessor usuario)
            throws Exception {
        var peticion = get(ruta);
        if (usuario != null) {
            peticion = peticion.with(usuario);
        }
        return mvc.perform(peticion).andReturn().getResponse().getStatus();
    }

    @Test
    void swagger_noEsPublico_anonimo401_user403() throws Exception {
        for (String ruta : new String[] {"/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html"}) {
            assertEquals(401, estado(ruta, null), ruta + " sin credenciales");
            assertEquals(403, estado(ruta, httpBasic("cajero", "Cajero12345")), ruta + " con rol USER");
        }
    }

    @Test
    void swagger_adminPasaLaAutorizacion() throws Exception {
        // No se afirma el contenido (lo genera springdoc): solo que la seguridad deja pasar al ADMIN.
        for (String ruta : new String[] {"/v3/api-docs", "/swagger-ui.html"}) {
            int codigo = estado(ruta, httpBasic("admin", "Admin12345"));
            assertTrue(codigo != 401 && codigo != 403, ruta + " con ADMIN devolvió " + codigo);
        }
    }

    @Test
    void admin_puedeCrearOtroAdmin_yListarSinContrasenas() throws Exception {
        String cuerpo = """
                {"nombre":"Admin 2","username":"admin2","password":"AdminClave123","rol":"ADMIN"}""";
        mvc.perform(post("/api/usuarios/admin").with(httpBasic("admin", "Admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());

        MvcResult lista = mvc.perform(get("/api/usuarios").with(httpBasic("admin", "Admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        String json = lista.getResponse().getContentAsString();
        assertFalse(json.contains("password"));
        assertFalse(json.contains("$2"));
    }

    @Test
    void user_noPuedeListarUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").with(httpBasic("cajero", "Cajero12345")))
                .andExpect(status().isForbidden());
    }
}
