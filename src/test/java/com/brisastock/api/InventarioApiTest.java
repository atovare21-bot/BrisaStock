package com.brisastock.api;

import com.brisastock.model.MovimientoInventario;
import com.brisastock.model.Producto;
import com.brisastock.model.TipoMovimiento;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Entradas, salidas, ajustes, historial, stock mínimo, seguridad y PUT sin stock. */
class InventarioApiTest extends PruebaApiBase {

    private RequestPostProcessor admin() {
        return httpBasic("admin", "Admin12345");
    }

    private RequestPostProcessor cajero() {
        return httpBasic("cajero", "Cajero12345");
    }

    private static String movimiento(Long productoId, int cantidad) {
        return "{\"productoId\":" + productoId + ",\"cantidad\":" + cantidad + "}";
    }

    private static String ajuste(Long productoId, int nuevoStock, String obs) {
        return "{\"productoId\":" + productoId + ",\"nuevoStock\":" + nuevoStock
                + (obs == null ? "" : ",\"observacion\":\"" + obs + "\"") + "}";
    }

    // ------------------------- ENTRADA -------------------------

    @Test
    void entrada_aumentaStock_yRegistraMovimiento() throws Exception {
        Producto p = crearProducto("Cola", 10, 1000.0, false);

        mvc.perform(post("/api/inventario/entradas").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":" + p.getId()
                                + ",\"cantidad\":5,\"referencia\":\"FACT-1\",\"observacion\":\"Compra\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.cantidad").value(5))
                .andExpect(jsonPath("$.stockAnterior").value(10))
                .andExpect(jsonPath("$.stockPosterior").value(15))
                .andExpect(jsonPath("$.usuario").value("admin"))
                .andExpect(jsonPath("$.referencia").value("FACT-1"));

        assertEquals(15, stockActual(p.getId()));
        assertEquals(1, movimientoRepository.count());
    }

    @Test
    void entrada_sobreProductoAgotado_loVuelveDisponible() throws Exception {
        Producto p = crearProducto("Cola", 0, 1000.0, false);
        assertEquals("Agotado", productoRepository.findById(p.getId()).orElseThrow().getEstado());

        mvc.perform(post("/api/inventario/entradas").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 4)))
                .andExpect(status().isCreated());

        Producto actualizado = productoRepository.findById(p.getId()).orElseThrow();
        assertEquals(4, actualizado.getStock());
        assertEquals("Disponible", actualizado.getEstado());
    }

    @Test
    void entrada_cantidadInvalida_devuelve400_yNoCambiaNada() throws Exception {
        Producto p = crearProducto("Cola", 10, 1000.0, false);
        for (String cuerpo : List.of(movimiento(p.getId(), 0), movimiento(p.getId(), -3),
                "{\"productoId\":" + p.getId() + "}", "{\"cantidad\":2}")) {
            mvc.perform(post("/api/inventario/entradas").with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(10, stockActual(p.getId()));
        assertEquals(0, movimientoRepository.count());
    }

    @Test
    void entrada_productoInexistente_devuelve404() throws Exception {
        mvc.perform(post("/api/inventario/entradas").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(movimiento(999999L, 1)))
                .andExpect(status().isNotFound());
        assertEquals(0, movimientoRepository.count());
    }

    // ------------------------- SALIDA -------------------------

    @Test
    void salida_disminuyeStock_yRegistraMovimiento() throws Exception {
        Producto p = crearProducto("Cola", 10, 1000.0, false);

        mvc.perform(post("/api/inventario/salidas").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("SALIDA"))
                .andExpect(jsonPath("$.stockAnterior").value(10))
                .andExpect(jsonPath("$.stockPosterior").value(7));

        assertEquals(7, stockActual(p.getId()));
        assertEquals(1, movimientoRepository.count());
    }

    @Test
    void salida_mayorAlStock_devuelve409_stockNoQuedaNegativo_ySinMovimiento() throws Exception {
        Producto p = crearProducto("Cola", 4, 1000.0, false);

        mvc.perform(post("/api/inventario/salidas").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        assertEquals(4, stockActual(p.getId()));
        assertEquals(0, movimientoRepository.count());
    }

    @Test
    void salida_quedaEnCero_marcaAgotado() throws Exception {
        Producto p = crearProducto("Cola", 4, 1000.0, false);

        mvc.perform(post("/api/inventario/salidas").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 4)))
                .andExpect(status().isCreated());

        Producto actualizado = productoRepository.findById(p.getId()).orElseThrow();
        assertEquals(0, actualizado.getStock());
        assertEquals("Agotado", actualizado.getEstado());
    }

    // ------------------------- AJUSTE -------------------------

    @Test
    void ajuste_fijaElStock_yDejaTrazabilidad() throws Exception {
        Producto p = crearProducto("Cola", 10, 1000.0, false);

        mvc.perform(post("/api/inventario/ajustes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste(p.getId(), 3, "Conteo físico")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.cantidad").value(7))
                .andExpect(jsonPath("$.stockAnterior").value(10))
                .andExpect(jsonPath("$.stockPosterior").value(3))
                .andExpect(jsonPath("$.observacion").value("Conteo físico"))
                .andExpect(jsonPath("$.usuario").value("admin"));

        assertEquals(3, stockActual(p.getId()));
        assertEquals(1, movimientoRepository.count());
    }

    @Test
    void ajuste_puedeSubirElStock() throws Exception {
        Producto p = crearProducto("Cola", 2, 1000.0, false);

        mvc.perform(post("/api/inventario/ajustes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste(p.getId(), 9, "Hallazgo en bodega")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidad").value(7))
                .andExpect(jsonPath("$.stockPosterior").value(9));
        assertEquals(9, stockActual(p.getId()));
    }

    @Test
    void ajuste_invalido_devuelve400_ySinCambios() throws Exception {
        Producto p = crearProducto("Cola", 10, 1000.0, false);
        List<String> invalidos = List.of(
                ajuste(p.getId(), 5, null),            // sin observación
                ajuste(p.getId(), 5, "   "),           // observación en blanco
                ajuste(p.getId(), -1, "negativo"),     // stock negativo
                ajuste(p.getId(), 10, "igual al actual"));
        for (String cuerpo : invalidos) {
            mvc.perform(post("/api/inventario/ajustes").with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(10, stockActual(p.getId()));
        assertEquals(0, movimientoRepository.count());
    }

    // ------------------------- HISTORIAL -------------------------

    @Test
    void historialDelProducto_listaLosMovimientos_delMasRecienteAlMasAntiguo() throws Exception {
        Producto p = crearProducto("Cola", 10, 1000.0, false);
        Producto otro = crearProducto("Otra", 10, 1000.0, false);

        mvc.perform(post("/api/inventario/entradas").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 5)));
        mvc.perform(post("/api/inventario/salidas").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 2)));
        mvc.perform(post("/api/inventario/entradas").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(movimiento(otro.getId(), 1)));

        mvc.perform(get("/api/inventario/productos/" + p.getId() + "/movimientos").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipo").value("SALIDA"))
                .andExpect(jsonPath("$[1].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$[0].productoId").value(p.getId()));

        mvc.perform(get("/api/inventario/movimientos").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mvc.perform(get("/api/inventario/movimientos").param("limite", "1").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void historialDeProductoInexistente_devuelve404() throws Exception {
        mvc.perform(get("/api/inventario/productos/999999/movimientos").with(admin()))
                .andExpect(status().isNotFound());
    }

    // ------------------------- PRODUCTOS -------------------------

    @Test
    void crearProductoConStock_registraStockInicialComoEntrada() throws Exception {
        mvc.perform(post("/api/productos").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Nuevo","precioCompra":1000,"precioVenta":1500,"stock":12,"stockMinimo":3}"""))
                .andExpect(status().isOk());

        List<MovimientoInventario> movs = movimientoRepository.findAll();
        assertEquals(1, movs.size());
        assertEquals(TipoMovimiento.ENTRADA, movs.get(0).getTipo());
        assertEquals(0, movs.get(0).getStockAnterior());
        assertEquals(12, movs.get(0).getStockPosterior());
        assertEquals("STOCK_INICIAL", movs.get(0).getReferencia());
        assertEquals("admin", movs.get(0).getUsuario().getUsername());
    }

    @Test
    void crearProductoConStockCero_noRegistraMovimiento() throws Exception {
        mvc.perform(post("/api/productos").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Nuevo","precioCompra":1000,"precioVenta":1500,"stock":0}"""))
                .andExpect(status().isOk());
        assertEquals(0, movimientoRepository.count());
    }

    @Test
    void put_sinCampoStock_actualizaDatos_yNoTocaElStock() throws Exception {
        Producto p = crearProducto("Cola", 5, 1000.0, false);
        mvc.perform(put("/api/productos/" + p.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Cola Zero","precioCompra":900,"precioVenta":1800,"stockMinimo":8}"""))
                .andExpect(status().isOk());

        Producto actualizado = productoRepository.findById(p.getId()).orElseThrow();
        assertEquals("Cola Zero", actualizado.getNombre());
        assertEquals(1800.0, actualizado.getPrecioVenta());
        assertEquals(8, actualizado.getStockMinimo());
        assertEquals(5, actualizado.getStock());
        assertEquals(0, movimientoRepository.count());
    }

    @Test
    void eliminar_productoConMovimientos_devuelve409YNoLoBorra() throws Exception {
        Producto p = crearProducto("Cola", 5, 1000.0, false);
        mvc.perform(post("/api/inventario/entradas").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 1)));

        mvc.perform(delete("/api/productos/" + p.getId()).with(admin()))
                .andExpect(status().isConflict());
        assertEquals(1, productoRepository.count());
    }

    // ------------------------- STOCK MÍNIMO -------------------------

    private Producto conMinimo(String nombre, int stock, int minimo) {
        Producto p = crearProducto(nombre, stock, 1000.0, false);
        p.setStockMinimo(minimo);
        return productoRepository.save(p);
    }

    @Test
    void reporteStockBajo_usaElStockMinimoDeCadaProducto() throws Exception {
        conMinimo("Igual", 10, 10);      // 10 <= 10 -> bajo
        conMinimo("Debajo", 9, 10);      // 9  <= 10 -> bajo
        conMinimo("Encima", 11, 10);     // 11 >  10 -> no
        conMinimo("MinimoBajo", 5, 2);   // con el valor fijo antiguo (5) saldría; ahora no

        mvc.perform(get("/api/reportes/stock-bajo").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", containsInAnyOrder("Igual", "Debajo")));
    }

    @Test
    void stockMinimo_reaccionaAlCambiarElStockPorInventario() throws Exception {
        Producto p = conMinimo("Cola", 20, 10);

        mvc.perform(get("/api/reportes/stock-bajo").with(admin()))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/inventario/salidas").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 10)));

        mvc.perform(get("/api/reportes/stock-bajo").with(admin()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Cola"));
    }

    // ------------------------- SEGURIDAD -------------------------

    @Test
    void sinAutenticacion_noPuedeModificarInventario_401() throws Exception {
        Producto p = crearProducto("Cola", 10, 1000.0, false);
        for (String ruta : List.of("entradas", "salidas")) {
            mvc.perform(post("/api/inventario/" + ruta)
                            .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 1)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/inventario/ajustes")
                        .contentType(MediaType.APPLICATION_JSON).content(ajuste(p.getId(), 1, "x")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inventario/movimientos")).andExpect(status().isUnauthorized());

        assertEquals(10, stockActual(p.getId()));
        assertEquals(0, movimientoRepository.count());
    }

    @Test
    void rolUser_puedeRegistrarEntrada_peroNoSalidasNiAjustesNiHistorial_403() throws Exception {
        Producto p = crearProducto("Cola", 10, 1000.0, false);

        mvc.perform(post("/api/inventario/entradas").with(cajero())
                        .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario").value("cajero"));

        mvc.perform(post("/api/inventario/salidas").with(cajero())
                        .contentType(MediaType.APPLICATION_JSON).content(movimiento(p.getId(), 1)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/inventario/ajustes").with(cajero())
                        .contentType(MediaType.APPLICATION_JSON).content(ajuste(p.getId(), 1, "x")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/inventario/movimientos").with(cajero()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/inventario/productos/" + p.getId() + "/movimientos").with(cajero()))
                .andExpect(status().isForbidden());

        assertEquals(12, stockActual(p.getId()));   // solo la entrada se aplicó
        assertEquals(1, movimientoRepository.count());
    }
}
