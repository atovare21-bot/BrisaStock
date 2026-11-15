package com.brisastock.api;

import com.brisastock.model.FechaVencimiento;
import com.brisastock.model.Producto;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 6: calidad e integridad de datos (fechas, stock bajo/agotado, desactivación lógica, PUT). */
class CalidadDatosProductoApiTest extends PruebaApiBase {

    private static final String NUEVO = """
            {"nombre":"Nuevo","precioCompra":1000,"precioVenta":1500,"stock":4,"stockMinimo":1,
             "vencido":false,"fechaVencimiento":"%s"}""";

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin", "Admin12345");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor cajero() {
        return httpBasic("cajero", "Cajero12345");
    }

    private Producto conFecha(String nombre, int stock, String fecha) {
        Producto p = crearProducto(nombre, stock, 1000.0, false);
        p.setFechaVencimiento(fecha);
        return productoRepository.save(p);
    }

    private String venta(Long id, int cantidad) {
        return "{\"productos\":[{\"productoId\":" + id + ",\"cantidad\":" + cantidad + "}]}";
    }

    // ---------- Fechas ----------

    @Test
    void utilidadFechas_validaSoloCalendarioRealIso() {
        assertTrue(FechaVencimiento.esValida("2027-02-28"));
        assertTrue(FechaVencimiento.esValida("2028-02-29"));
        assertFalse(FechaVencimiento.esValida("2027-02-29"));
        assertFalse(FechaVencimiento.esValida("2026-02-30"));
        assertFalse(FechaVencimiento.esValida("2026-13-01"));
        assertFalse(FechaVencimiento.esValida("31/12/2027"));
        assertFalse(FechaVencimiento.esValida("2027-1-5"));
        assertFalse(FechaVencimiento.esValida("pronto"));
        assertFalse(FechaVencimiento.esValida(null));
        LocalDate hoy = LocalDate.of(2026, 6, 15);
        assertTrue(FechaVencimiento.estaVencida("2026-06-14", hoy));
        assertFalse(FechaVencimiento.estaVencida("2026-06-15", hoy));   // sirve todo el día
        assertFalse(FechaVencimiento.estaVencida("14/06/2026", hoy));   // formato antiguo = desconocida
    }

    @Test
    void crear_fechaImposibleOFormatoIncorrecto_devuelve400YNoGuarda() throws Exception {
        for (String mala : new String[]{"2026-02-30", "2026-13-01", "31/12/2027", "mañana"}) {
            mvc.perform(post("/api/productos").with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(NUEVO.formatted(mala)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, productoRepository.count());
    }

    @Test
    void crear_fechaValidaOVacia_seAcepta() throws Exception {
        mvc.perform(post("/api/productos").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NUEVO.formatted("2099-12-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vencidoPorFecha").value(false))
                .andExpect(jsonPath("$.activo").value(true));
        mvc.perform(post("/api/productos").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NUEVO.formatted("")))
                .andExpect(status().isOk());
        assertEquals(2, productoRepository.count());
    }

    @Test
    void actualizar_fechaInvalida_devuelve400_perofechaAntiguaSinCambiosSeTolera() throws Exception {
        Producto legado = conFecha("Legado", 5, "31/12/2027");

        // Nueva fecha inválida -> 400 y no se toca el registro
        mvc.perform(put("/api/productos/" + legado.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Legado\",\"precioCompra\":1,\"precioVenta\":2,\"fechaVencimiento\":\"2027-02-30\"}"))
                .andExpect(status().isBadRequest());
        assertEquals("31/12/2027", productoRepository.findById(legado.getId()).orElseThrow().getFechaVencimiento());

        // Reenviar el MISMO valor antiguo (la UI lo hace al editar otros campos) -> se acepta sin convertirlo
        mvc.perform(put("/api/productos/" + legado.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Legado 2\",\"precioCompra\":1,\"precioVenta\":2,\"fechaVencimiento\":\"31/12/2027\"}"))
                .andExpect(status().isOk());
        Producto tras = productoRepository.findById(legado.getId()).orElseThrow();
        assertEquals("Legado 2", tras.getNombre());
        assertEquals("31/12/2027", tras.getFechaVencimiento());
    }

    @Test
    void vencidoPorFecha_sigueFechaPasadaFuturoYFormatoAntiguo() throws Exception {
        Producto pasado = conFecha("Pasado", 5, "2020-01-01");
        Producto futuro = conFecha("Futuro", 5, "2099-01-01");
        Producto antiguo = conFecha("Antiguo", 5, "01/01/2020");

        mvc.perform(get("/api/productos/" + pasado.getId()).with(admin()))
                .andExpect(jsonPath("$.vencidoPorFecha").value(true));
        mvc.perform(get("/api/productos/" + futuro.getId()).with(admin()))
                .andExpect(jsonPath("$.vencidoPorFecha").value(false));
        mvc.perform(get("/api/productos/" + antiguo.getId()).with(admin()))
                .andExpect(jsonPath("$.vencidoPorFecha").value(false));
    }

    @Test
    void venta_productoVencidoPorFecha_devuelve409_yNoTocaStockNiMovimientos() throws Exception {
        Producto p = conFecha("Pasado", 5, "2020-01-01");
        long movimientos = movimientoRepository.count();

        mvc.perform(post("/api/ventas").with(cajero())
                        .contentType(MediaType.APPLICATION_JSON).content(venta(p.getId(), 1)))
                .andExpect(status().isConflict());
        assertEquals(5, stockActual(p.getId()));
        assertEquals(movimientos, movimientoRepository.count());
        assertEquals(0, ventaRepository.count());
    }

    @Test
    void venta_fechaFuturaOFormatoAntiguo_sePermite() throws Exception {
        Producto futuro = conFecha("Futuro", 5, "2099-01-01");
        Producto antiguo = conFecha("Antiguo", 5, "01/01/2020");
        for (Producto p : new Producto[]{futuro, antiguo}) {
            mvc.perform(post("/api/ventas").with(cajero())
                            .contentType(MediaType.APPLICATION_JSON).content(venta(p.getId(), 1)))
                    .andExpect(status().isOk());
            assertEquals(4, stockActual(p.getId()));
        }
    }

    @Test
    void reporteVencidos_incluyeIndicadorManualYFechaPasada_peroNoFuturasNiAntiguas() throws Exception {
        crearProducto("Manual", 5, 1000.0, true);
        conFecha("Pasado", 5, "2020-01-01");
        conFecha("Futuro", 5, "2099-01-01");
        conFecha("Antiguo", 5, "01/01/2020");

        mvc.perform(get("/api/reportes/vencidos").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------- Stock bajo y agotados ----------

    @Test
    void reporteAgotados_usaStockReal_noElTextoDeEstado() throws Exception {
        Producto sinStock = crearProducto("SinStock", 0, 1000.0, false);
        sinStock.setEstado("Disponible");                      // texto desfasado
        productoRepository.save(sinStock);
        Producto conStock = crearProducto("ConStock", 5, 1000.0, false);
        conStock.setEstado("Agotado");                         // texto desfasado
        productoRepository.save(conStock);

        mvc.perform(get("/api/reportes/agotados").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("SinStock"));
    }

    @Test
    void stockBajo_unaSolaRegla_rutaAntiguaIgnoraElNumero() throws Exception {
        crearProducto("Bajo", 2, 1000.0, false);       // stockMinimo 2 -> bajo
        crearProducto("Normal", 50, 1000.0, false);

        for (String ruta : new String[]{"/api/productos/stock-bajo", "/api/productos/stock-bajo/1000",
                "/api/reportes/stock-bajo"}) {
            mvc.perform(get(ruta).with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].nombre").value("Bajo"));
        }
        mvc.perform(get("/api/productos/stock-bajo").with(cajero())).andExpect(status().isOk());
    }

    // ---------- PUT conserva los opcionales ----------

    @Test
    void put_camposOpcionalesOmitidos_seConservan_yNoHayMovimientos() throws Exception {
        Producto p = conFecha("Cola", 5, "2099-05-05");
        p.setVencido(false);
        p = productoRepository.save(p);

        mvc.perform(put("/api/productos/" + p.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Cola 2\",\"precioCompra\":900,\"precioVenta\":1800}"))
                .andExpect(status().isOk());

        Producto t = productoRepository.findById(p.getId()).orElseThrow();
        assertEquals("Cola 2", t.getNombre());
        assertEquals("Marca", t.getMarca());
        assertEquals("Bebidas", t.getCategoria());
        assertEquals(2, t.getStockMinimo());
        assertEquals("2099-05-05", t.getFechaVencimiento());
        assertEquals(Boolean.FALSE, t.getVencido());
        assertEquals(5, t.getStock());
        assertEquals(0, movimientoRepository.count());
    }

    @Test
    void put_cadenaVacia_borraAPropositoMarcaYFecha() throws Exception {
        Producto p = conFecha("Cola", 5, "2099-05-05");

        mvc.perform(put("/api/productos/" + p.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Cola\",\"marca\":\"\",\"precioCompra\":900,\"precioVenta\":1800,\"fechaVencimiento\":\"\"}"))
                .andExpect(status().isOk());

        Producto t = productoRepository.findById(p.getId()).orElseThrow();
        assertEquals(null, t.getMarca());
        assertEquals(null, t.getFechaVencimiento());
        assertEquals("Bebidas", t.getCategoria());
    }

    @Test
    void put_precioInvalido_sigueDevolviendo400() throws Exception {
        Producto p = crearProducto("Cola", 5, 1000.0, false);
        mvc.perform(put("/api/productos/" + p.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Cola\",\"precioCompra\":-1,\"precioVenta\":0}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Desactivación lógica ----------

    @Test
    void desactivar_soloAdmin_yUsuarioNormalRecibe403() throws Exception {
        Producto p = crearProducto("Cola", 5, 1000.0, false);

        mvc.perform(patch("/api/productos/" + p.getId() + "/desactivar").with(cajero()))
                .andExpect(status().isForbidden());
        assertTrue(productoRepository.findById(p.getId()).orElseThrow().estaActivo());

        mvc.perform(patch("/api/productos/" + p.getId() + "/desactivar").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        assertFalse(productoRepository.findById(p.getId()).orElseThrow().estaActivo());

        mvc.perform(patch("/api/productos/999999/desactivar").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void productoDesactivado_noSePuedeVender_yReactivadoSi() throws Exception {
        Producto p = crearProducto("Cola", 5, 1000.0, false);
        mvc.perform(patch("/api/productos/" + p.getId() + "/desactivar").with(admin()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/ventas").with(cajero())
                        .contentType(MediaType.APPLICATION_JSON).content(venta(p.getId(), 1)))
                .andExpect(status().isConflict());
        assertEquals(5, stockActual(p.getId()));
        assertEquals(0, ventaRepository.count());

        mvc.perform(patch("/api/productos/" + p.getId() + "/activar").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
        mvc.perform(post("/api/ventas").with(cajero())
                        .contentType(MediaType.APPLICATION_JSON).content(venta(p.getId(), 1)))
                .andExpect(status().isOk());
        assertEquals(4, stockActual(p.getId()));
    }

    @Test
    void desactivar_conservaHistorial_ySigueSiendoPosibleCancelarLaVenta() throws Exception {
        Producto p = crearProducto("Cola", 5, 1000.0, false);
        mvc.perform(post("/api/ventas").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(venta(p.getId(), 2)))
                .andExpect(status().isOk());
        long ventas = ventaRepository.count();
        long movimientos = movimientoRepository.count();
        long detalles = detalleVentaRepository.count();

        mvc.perform(patch("/api/productos/" + p.getId() + "/desactivar").with(admin()))
                .andExpect(status().isOk());

        // nada se borró y el producto sigue consultable por id y en el listado
        assertEquals(ventas, ventaRepository.count());
        assertEquals(movimientos, movimientoRepository.count());
        assertEquals(detalles, detalleVentaRepository.count());
        mvc.perform(get("/api/productos/" + p.getId()).with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/productos").with(admin()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].activo").value(false));
        mvc.perform(get("/api/ventas").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // cancelar la venta de un producto desactivado devuelve el stock y registra la devolución
        Long ventaId = ventaRepository.findAll().get(0).getId();
        mvc.perform(post("/api/ventas/" + ventaId + "/cancelar").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
        assertEquals(5, stockActual(p.getId()));
        assertEquals(movimientos + 1, movimientoRepository.count());
    }

    @Test
    void productoDesactivado_noApareceEnAlertasDeStockBajoAgotadosNiVencidos() throws Exception {
        Producto bajo = crearProducto("Bajo", 1, 1000.0, false);
        Producto agotado = crearProducto("Agotado", 0, 1000.0, false);
        Producto vencido = crearProducto("Vencido", 5, 1000.0, true);
        for (Producto p : new Producto[]{bajo, agotado, vencido}) {
            mvc.perform(patch("/api/productos/" + p.getId() + "/desactivar").with(admin()))
                    .andExpect(status().isOk());
        }
        for (String r : new String[]{"stock-bajo", "agotados", "vencidos"}) {
            mvc.perform(get("/api/reportes/" + r).with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
