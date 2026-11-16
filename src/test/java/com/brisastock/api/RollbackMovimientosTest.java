package com.brisastock.api;

import com.brisastock.dto.ProductoVentaDTO;
import com.brisastock.dto.VentaRequestDTO;
import com.brisastock.model.EstadoVenta;
import com.brisastock.model.MovimientoInventario;
import com.brisastock.model.Producto;
import com.brisastock.model.Venta;
import com.brisastock.repository.MovimientoInventarioRepository;
import com.brisastock.service.VentaService;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Rollback con una BD real (H2 en memoria): se reemplaza el repositorio de
 * movimientos por un mock que falla, DESPUÉS de que el servicio ya cambió el
 * stock de otros productos en la misma transacción. Nada debe quedar a medias.
 */
class RollbackMovimientosTest extends PruebaApiBase {

    @Autowired
    private VentaService ventaService;

    @MockitoBean
    private MovimientoInventarioRepository movimientoRepositoryFallido;

    private static ProductoVentaDTO linea(Long id, int cantidad) {
        ProductoVentaDTO l = new ProductoVentaDTO();
        l.setProductoId(id);
        l.setCantidad(cantidad);
        return l;
    }

    private static VentaRequestDTO solicitud(ProductoVentaDTO... lineas) {
        VentaRequestDTO r = new VentaRequestDTO();
        r.setProductos(List.of(lineas));
        return r;
    }

    @Test
    void venta_siFallaElRegistroDelMovimiento_serevierteTodo() {
        Producto a = crearProducto("A", 10, 1000.0, false);
        Producto b = crearProducto("B", 10, 2000.0, false);

        autenticarComo("admin");
        doThrow(new RuntimeException("fallo simulado al registrar el movimiento"))
                .when(movimientoRepositoryFallido).save(any(MovimientoInventario.class));

        assertThrows(RuntimeException.class,
                () -> ventaService.registrarVenta(solicitud(linea(a.getId(), 3), linea(b.getId(), 4))));

        assertEquals(10, stockActual(a.getId()));
        assertEquals(10, stockActual(b.getId()));
        assertEquals(0, ventaRepository.count());
        assertEquals(0, detalleVentaRepository.count());
    }

    @Test
    void cancelacion_siFallaAMitad_noDevuelveStockParcial_yLaVentaSigueActiva() {
        Producto a = crearProducto("A", 10, 1000.0, false);
        Producto b = crearProducto("B", 10, 2000.0, false);

        // La venta se registra bien (el mock de movimientos acepta y no hace nada)
        autenticarComo("admin");
        Venta venta = ventaService.registrarVenta(solicitud(linea(a.getId(), 3), linea(b.getId(), 4)));
        assertEquals(7, stockActual(a.getId()));
        assertEquals(6, stockActual(b.getId()));

        // En la cancelación, el 1.er movimiento DEVOLUCION pasa y el 2.º falla
        doReturn(null).doThrow(new RuntimeException("fallo simulado en la 2.ª devolución"))
                .when(movimientoRepositoryFallido).save(any(MovimientoInventario.class));

        Long id = venta.getId();
        assertThrows(RuntimeException.class, () -> ventaService.cancelarVenta(id, "prueba"));

        // Rollback completo: el stock sigue como después de la venta
        assertEquals(7, stockActual(a.getId()));
        assertEquals(6, stockActual(b.getId()));
        assertEquals(EstadoVenta.ACTIVA, ventaRepository.findById(id).orElseThrow().getEstado());
    }
}
