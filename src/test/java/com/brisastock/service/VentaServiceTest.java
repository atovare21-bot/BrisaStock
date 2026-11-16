package com.brisastock.service;

import com.brisastock.dto.ProductoVentaDTO;
import com.brisastock.dto.VentaRequestDTO;
import com.brisastock.exception.ConflictoNegocioException;
import com.brisastock.exception.RecursoNoEncontradoException;
import com.brisastock.exception.SolicitudInvalidaException;
import com.brisastock.model.Producto;
import com.brisastock.model.TipoMovimiento;
import com.brisastock.model.Venta;
import com.brisastock.repository.ProductoRepository;
import com.brisastock.repository.VentaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pruebas unitarias (sin BD) de las reglas de VentaService. */
@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private VentaRepository ventaRepository;

    // Fase 2: el registro de movimientos se prueba aparte (pruebas de API);
    // aquí solo se aísla para que estas pruebas unitarias sigan sin BD.
    @Mock
    private InventarioService inventarioService;

    // El usuario que registra la venta se resuelve aparte; aquí no hay BD ni sesión.
    @Mock
    private UsuarioSesion usuarioSesion;

    @InjectMocks
    private VentaService ventaService;

    private static Producto producto(long id, int stock, Double precio, Boolean vencido) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Producto " + id);
        p.setStock(stock);
        p.setPrecioVenta(precio);
        p.setVencido(vencido);
        return p;
    }

    private static ProductoVentaDTO linea(Long productoId, Integer cantidad) {
        ProductoVentaDTO l = new ProductoVentaDTO();
        l.setProductoId(productoId);
        l.setCantidad(cantidad);
        return l;
    }

    private static VentaRequestDTO solicitud(ProductoVentaDTO... lineas) {
        VentaRequestDTO r = new VentaRequestDTO();
        r.setProductos(List.of(lineas));
        return r;
    }

    @Test
    void ventaValida_calculaTotalYDescuentaStock() {
        Producto p = producto(1, 10, 100.0, false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(i -> i.getArgument(0));

        Venta venta = ventaService.registrarVenta(solicitud(linea(1L, 3)));

        assertEquals(300.0, venta.getTotal());
        assertEquals(7, p.getStock());
        // La línea guarda cantidad y precio del momento; el subtotal se calcula
        assertEquals(3, venta.getDetalles().get(0).getCantidad());
        assertEquals(100.0, venta.getDetalles().get(0).getPrecioUnitario());
        assertEquals(300.0, venta.getDetalles().get(0).getSubtotal());
        // El stock antes/después queda en el movimiento de inventario (10 -> 7)
        verify(inventarioService).registrarMovimiento(
                eq(p), eq(TipoMovimiento.VENTA), eq(3), eq(10), eq(7), any(), any());
    }

    @Test
    void ventaDeTodoElStock_dejaProductoAgotado() {
        Producto p = producto(1, 4, 100.0, false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(i -> i.getArgument(0));

        ventaService.registrarVenta(solicitud(linea(1L, 4)));

        assertEquals(0, p.getStock());
        assertEquals("Agotado", p.getEstado());
    }

    @Test
    void stockInsuficiente_lanzaConflicto_yNoGuardaNada() {
        Producto p = producto(1, 2, 100.0, false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThrows(ConflictoNegocioException.class,
                () -> ventaService.registrarVenta(solicitud(linea(1L, 3))));

        assertEquals(2, p.getStock());
        verify(productoRepository, never()).save(any());
        verify(ventaRepository, never()).save(any());
    }

    @Test
    void segundaLineaSinStock_noDescuentaLaPrimera() {
        Producto a = producto(1, 10, 100.0, false);
        Producto b = producto(2, 1, 100.0, false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(a));
        when(productoRepository.findById(2L)).thenReturn(Optional.of(b));

        assertThrows(ConflictoNegocioException.class,
                () -> ventaService.registrarVenta(solicitud(linea(1L, 5), linea(2L, 5))));

        assertEquals(10, a.getStock());
        verify(productoRepository, never()).save(any());
    }

    @Test
    void mismoProductoEnVariasLineas_acumulaCantidadContraElStock() {
        Producto p = producto(1, 5, 100.0, false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThrows(ConflictoNegocioException.class,
                () -> ventaService.registrarVenta(solicitud(linea(1L, 3), linea(1L, 3))));

        assertEquals(5, p.getStock());
    }

    @Test
    void productoInexistente_lanzaNoEncontrado() {
        when(productoRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> ventaService.registrarVenta(solicitud(linea(9L, 1))));
    }

    @Test
    void productoVencido_lanzaConflicto() {
        when(productoRepository.findById(1L))
                .thenReturn(Optional.of(producto(1, 10, 100.0, true)));

        assertThrows(ConflictoNegocioException.class,
                () -> ventaService.registrarVenta(solicitud(linea(1L, 1))));
    }

    @Test
    void precioInvalido_lanzaConflicto() {
        when(productoRepository.findById(1L))
                .thenReturn(Optional.of(producto(1, 10, null, false)));

        assertThrows(ConflictoNegocioException.class,
                () -> ventaService.registrarVenta(solicitud(linea(1L, 1))));
    }

    @Test
    void cantidadesInvalidas_seRechazanAntesDeTocarProductos() {
        assertThrows(SolicitudInvalidaException.class,
                () -> ventaService.registrarVenta(solicitud(linea(1L, 0))));
        assertThrows(SolicitudInvalidaException.class,
                () -> ventaService.registrarVenta(solicitud(linea(1L, -5))));
        assertThrows(SolicitudInvalidaException.class,
                () -> ventaService.registrarVenta(solicitud(linea(1L, null))));
        assertThrows(SolicitudInvalidaException.class,
                () -> ventaService.registrarVenta(solicitud(linea(null, 1))));

        verifyNoInteractions(productoRepository);
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void solicitudVaciaONula_seRechaza() {
        assertThrows(SolicitudInvalidaException.class,
                () -> ventaService.registrarVenta(null));
        assertThrows(SolicitudInvalidaException.class,
                () -> ventaService.registrarVenta(new VentaRequestDTO()));
        assertThrows(SolicitudInvalidaException.class,
                () -> ventaService.registrarVenta(solicitud()));
    }
}
