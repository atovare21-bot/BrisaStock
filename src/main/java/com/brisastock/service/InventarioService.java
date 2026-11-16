package com.brisastock.service;

import com.brisastock.dto.AjusteStockRequestDTO;
import com.brisastock.dto.MovimientoInventarioDTO;
import com.brisastock.dto.MovimientoStockRequestDTO;
import com.brisastock.exception.ConflictoNegocioException;
import com.brisastock.exception.RecursoNoEncontradoException;
import com.brisastock.exception.SolicitudInvalidaException;
import com.brisastock.model.MovimientoInventario;
import com.brisastock.model.Producto;
import com.brisastock.model.TipoMovimiento;
import com.brisastock.repository.MovimientoInventarioRepository;
import com.brisastock.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Única vía controlada para cambiar el stock fuera de las ventas.
 *
 * Cada operación es una transacción: el cambio de stock y su movimiento se
 * confirman juntos o se revierten juntos. La concurrencia la resuelve el
 * @Version de Producto (dos cambios simultáneos al mismo producto: el segundo
 * recibe un conflicto -> HTTP 409).
 */
@Service
public class InventarioService {

    static final int LIMITE_DEFECTO = 200;
    static final int LIMITE_MAXIMO = 1000;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @Autowired
    private UsuarioSesion usuarioSesion;

    // ------------------------------------------------------------------
    // Operaciones de inventario
    // ------------------------------------------------------------------

    @Transactional
    public MovimientoInventarioDTO registrarEntrada(MovimientoStockRequestDTO dto) {
        validarCantidad(dto.cantidad());
        Producto producto = buscarProducto(dto.productoId());

        int anterior = stockDe(producto);
        long nuevo = (long) anterior + dto.cantidad();
        if (nuevo > Integer.MAX_VALUE) {
            throw new SolicitudInvalidaException("La cantidad supera el stock máximo permitido");
        }

        return MovimientoInventarioDTO.desde(aplicar(producto, TipoMovimiento.ENTRADA,
                dto.cantidad(), (int) nuevo, dto.referencia(), dto.observacion()));
    }

    @Transactional
    public MovimientoInventarioDTO registrarSalida(MovimientoStockRequestDTO dto) {
        validarCantidad(dto.cantidad());
        Producto producto = buscarProducto(dto.productoId());

        int anterior = stockDe(producto);
        if (dto.cantidad() > anterior) {
            throw new ConflictoNegocioException(
                    "Stock insuficiente para " + producto.getNombre()
                            + ". Solicitado: " + dto.cantidad() + ". Disponible: " + anterior);
        }

        return MovimientoInventarioDTO.desde(aplicar(producto, TipoMovimiento.SALIDA,
                dto.cantidad(), anterior - dto.cantidad(), dto.referencia(), dto.observacion()));
    }

    @Transactional
    public MovimientoInventarioDTO registrarAjuste(AjusteStockRequestDTO dto) {
        if (dto.nuevoStock() == null || dto.nuevoStock() < 0) {
            throw new SolicitudInvalidaException("El stock no puede ser negativo");
        }
        if (dto.observacion() == null || dto.observacion().isBlank()) {
            throw new SolicitudInvalidaException("Todo ajuste debe indicar el motivo en la observación");
        }
        Producto producto = buscarProducto(dto.productoId());

        int anterior = stockDe(producto);
        if (dto.nuevoStock() == anterior) {
            throw new SolicitudInvalidaException(
                    "El nuevo stock es igual al stock actual (" + anterior + "); no hay nada que ajustar");
        }

        // cantidad = magnitud de la diferencia; el sentido se ve en anterior -> posterior
        int diferencia = Math.abs(dto.nuevoStock() - anterior);
        return MovimientoInventarioDTO.desde(aplicar(producto, TipoMovimiento.AJUSTE,
                diferencia, dto.nuevoStock(), null, dto.observacion()));
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MovimientoInventarioDTO> listarMovimientos(Integer limite) {
        return movimientoRepository
                .findAllByOrderByFechaDescIdDesc(PageRequest.of(0, normalizar(limite)))
                .stream().map(MovimientoInventarioDTO::desde).toList();
    }

    @Transactional(readOnly = true)
    public List<MovimientoInventarioDTO> listarMovimientosDeProducto(Long productoId, Integer limite) {
        if (!productoRepository.existsById(productoId)) {
            throw new RecursoNoEncontradoException("Producto no encontrado con id " + productoId);
        }
        return movimientoRepository
                .findByProductoIdOrderByFechaDescIdDesc(productoId, PageRequest.of(0, normalizar(limite)))
                .stream().map(MovimientoInventarioDTO::desde).toList();
    }

    // ------------------------------------------------------------------
    // Piezas compartidas con VentaService / ProductoService
    // ------------------------------------------------------------------

    /**
     * Fija el nuevo stock del producto y registra el
     * movimiento. Debe llamarse dentro de una transacción activa.
     * Defensa final: nunca deja stock negativo.
     */
    public MovimientoInventario aplicar(Producto producto, TipoMovimiento tipo, int cantidad,
                                        int nuevoStock, String referencia, String observacion) {
        if (nuevoStock < 0) {
            throw new ConflictoNegocioException(
                    "El stock de " + producto.getNombre() + " no puede quedar negativo");
        }
        int anterior = stockDe(producto);

        producto.setStock(nuevoStock);
        productoRepository.save(producto);

        return registrarMovimiento(producto, tipo, cantidad, anterior, nuevoStock, referencia, observacion);
    }

    /** Solo crea el registro del movimiento (el stock ya fue cambiado por quien llama). */
    public MovimientoInventario registrarMovimiento(Producto producto, TipoMovimiento tipo, int cantidad,
                                                    int stockAnterior, int stockPosterior,
                                                    String referencia, String observacion) {
        MovimientoInventario m = new MovimientoInventario();
        m.setProducto(producto);
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setStockAnterior(stockAnterior);
        m.setStockPosterior(stockPosterior);
        m.setFecha(LocalDateTime.now());
        m.setUsuario(usuarioSesion.requerido());
        m.setReferencia(limpiar(referencia));
        m.setObservacion(limpiar(observacion));
        return movimientoRepository.save(m);
    }

    // Un stock nulo en la BD se trata como 0
    static int stockDe(Producto producto) {
        return producto.getStock() == null ? 0 : producto.getStock();
    }

    // ------------------------------------------------------------------

    private Producto buscarProducto(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Producto no encontrado con id " + id));
    }

    private void validarCantidad(Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new SolicitudInvalidaException("La cantidad debe ser mayor que cero");
        }
    }

    private int normalizar(Integer limite) {
        if (limite == null) {
            return LIMITE_DEFECTO;
        }
        return Math.min(Math.max(limite, 1), LIMITE_MAXIMO);
    }

    private String limpiar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }
}
