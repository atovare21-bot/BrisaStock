package com.brisastock.api;

import com.brisastock.model.EstadoVenta;
import com.brisastock.model.MovimientoInventario;
import com.brisastock.model.Producto;
import com.brisastock.model.TipoMovimiento;
import com.brisastock.model.Venta;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ventas que generan movimientos y cancelación/devolución de ventas. */
class VentaInventarioApiTest extends PruebaApiBase {

    private RequestPostProcessor admin() {
        return httpBasic("admin", "Admin12345");
    }

    private RequestPostProcessor cajero() {
        return httpBasic("cajero", "Cajero12345");
    }

    private Venta vender(RequestPostProcessor usuario, Object... idsYCantidades) throws Exception {
        StringBuilder lineas = new StringBuilder();
        for (int i = 0; i < idsYCantidades.length; i += 2) {
            if (i > 0) {
                lineas.append(",");
            }
            lineas.append("{\"productoId\":").append(idsYCantidades[i])
                    .append(",\"cantidad\":").append(idsYCantidades[i + 1]).append("}");
        }
        mvc.perform(post("/api/ventas").with(usuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productos\":[" + lineas + "]}"))
                .andExpect(status().isOk());
        List<Venta> ventas = ventaRepository.findAll();
        return ventas.stream().max(Comparator.comparing(Venta::getId)).orElseThrow();
    }

    // ------------------------- VENTA -------------------------

    @Test
    void venta_generaUnMovimientoVentaPorLinea_conReferenciaYUsuario() throws Exception {
        Producto a = crearProducto("A", 10, 1000.0, false);
        Producto b = crearProducto("B", 8, 2000.0, false);

        Venta venta = vender(cajero(), a.getId(), 3, b.getId(), 2);

        List<MovimientoInventario> movs = movimientoRepository.findAll();
        assertEquals(2, movs.size());
        for (MovimientoInventario m : movs) {
            assertEquals(TipoMovimiento.VENTA, m.getTipo());
            assertEquals("VENTA #" + venta.getId(), m.getReferencia());
            assertEquals("cajero", m.getUsuario().getUsername());
            assertNotNull(m.getFecha());
            assertEquals(m.getStockAnterior() - m.getCantidad(), m.getStockPosterior());
        }
        assertTrue(movs.stream().anyMatch(m -> m.getCantidad() == 3
                && m.getStockAnterior() == 10 && m.getStockPosterior() == 7));
        assertTrue(movs.stream().anyMatch(m -> m.getCantidad() == 2
                && m.getStockAnterior() == 8 && m.getStockPosterior() == 6));
        assertEquals(7, stockActual(a.getId()));
        assertEquals(6, stockActual(b.getId()));
    }

    @Test
    void ventaRechazadaPorStockInsuficiente_noDejaMovimientos() throws Exception {
        Producto a = crearProducto("A", 2, 1000.0, false);

        mvc.perform(post("/api/ventas").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productos\":[{\"productoId\":" + a.getId() + ",\"cantidad\":5}]}"))
                .andExpect(status().isConflict());

        assertEquals(0, movimientoRepository.count());
        assertEquals(2, stockActual(a.getId()));
    }

    // ------------------------- CANCELACIÓN -------------------------

    @Test
    void cancelarVenta_devuelveStock_registraDevolucion_yConservaLaVenta() throws Exception {
        Producto a = crearProducto("A", 10, 1000.0, false);
        Producto b = crearProducto("B", 8, 2000.0, false);
        Venta venta = vender(admin(), a.getId(), 3, b.getId(), 2);

        mvc.perform(post("/api/ventas/" + venta.getId() + "/cancelar").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cliente se arrepintió\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelacion").value("Cliente se arrepintió"))
                .andExpect(jsonPath("$.usuarioCancelacion").value("admin"));

        // stock restituido
        assertEquals(10, stockActual(a.getId()));
        assertEquals(8, stockActual(b.getId()));

        // 2 movimientos VENTA + 2 DEVOLUCION
        List<MovimientoInventario> movs = movimientoRepository.findAll();
        assertEquals(4, movs.size());
        List<MovimientoInventario> devoluciones = movs.stream()
                .filter(m -> m.getTipo() == TipoMovimiento.DEVOLUCION).toList();
        assertEquals(2, devoluciones.size());
        for (MovimientoInventario d : devoluciones) {
            assertEquals("VENTA #" + venta.getId(), d.getReferencia());
            assertEquals(d.getStockAnterior() + d.getCantidad(), d.getStockPosterior());
            assertTrue(d.getObservacion().contains("Cliente se arrepintió"));
        }

        // la venta y su detalle se conservan (no se borra nada)
        assertEquals(1, ventaRepository.count());
        assertEquals(2, detalleVentaRepository.count());
        Venta guardada = ventaRepository.findById(venta.getId()).orElseThrow();
        assertEquals(EstadoVenta.CANCELADA, guardada.getEstado());
        assertNotNull(guardada.getFechaCancelacion());
    }

    @Test
    void cancelarVenta_sinCuerpo_funcionaYQuedaCancelada() throws Exception {
        Producto a = crearProducto("A", 10, 1000.0, false);
        Venta venta = vender(admin(), a.getId(), 4);

        mvc.perform(post("/api/ventas/" + venta.getId() + "/cancelar").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
        assertEquals(10, stockActual(a.getId()));
    }

    @Test
    void cancelarVenta_dosVeces_devuelve409_yNoDevuelveStockDosVeces() throws Exception {
        Producto a = crearProducto("A", 10, 1000.0, false);
        Venta venta = vender(admin(), a.getId(), 3);

        mvc.perform(post("/api/ventas/" + venta.getId() + "/cancelar").with(admin()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/ventas/" + venta.getId() + "/cancelar").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        assertEquals(10, stockActual(a.getId()));
        assertEquals(2, movimientoRepository.count());   // VENTA + una sola DEVOLUCION
    }

    @Test
    void cancelarVenta_agotada_vuelveADisponible() throws Exception {
        Producto a = crearProducto("A", 3, 1000.0, false);
        Venta venta = vender(admin(), a.getId(), 3);
        assertEquals("Agotado", productoRepository.findById(a.getId()).orElseThrow().getEstado());

        mvc.perform(post("/api/ventas/" + venta.getId() + "/cancelar").with(admin()))
                .andExpect(status().isOk());

        Producto actualizado = productoRepository.findById(a.getId()).orElseThrow();
        assertEquals(3, actualizado.getStock());
        assertEquals("Disponible", actualizado.getEstado());
    }

    @Test
    void cancelarVenta_inexistente_devuelve404() throws Exception {
        mvc.perform(post("/api/ventas/999999/cancelar").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelarVenta_seguridad_401SinAutenticacion_403ParaUser() throws Exception {
        Producto a = crearProducto("A", 10, 1000.0, false);
        Venta venta = vender(cajero(), a.getId(), 3);

        mvc.perform(post("/api/ventas/" + venta.getId() + "/cancelar"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ventas/" + venta.getId() + "/cancelar").with(cajero()))
                .andExpect(status().isForbidden());

        assertEquals(7, stockActual(a.getId()));
        assertEquals(EstadoVenta.ACTIVA,
                ventaRepository.findById(venta.getId()).orElseThrow().getEstado());
    }
}
