package com.brisastock.service;

import com.brisastock.dto.ProductoVentaDTO;
import com.brisastock.dto.VentaRequestDTO;
import com.brisastock.exception.ConflictoNegocioException;
import com.brisastock.exception.RecursoNoEncontradoException;
import com.brisastock.exception.SolicitudInvalidaException;
import com.brisastock.model.DetalleVenta;
import com.brisastock.model.EstadoVenta;
import com.brisastock.model.Producto;
import com.brisastock.model.TipoMovimiento;
import com.brisastock.model.Venta;
import com.brisastock.repository.ProductoRepository;
import com.brisastock.repository.VentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VentaService {

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private UsuarioSesion usuarioSesion;

    // Cambio de stock pendiente de registrar como movimiento VENTA
    private record CambioStock(Producto producto, int cantidad, int antes, int despues) {
    }

    /**
     * Registra una venta COMPLETA como una sola transacción.
     *
     * Flujo:
     *   1. validar la solicitud (lista, cantidades, ids)
     *   2. validar TODOS los productos antes de modificar nada
     *      (existen, no vencidos, precio válido, stock suficiente)
     *   3. calcular totales, descontar stock y crear los detalles
     *   4. guardar la venta
     *
     * Si cualquier paso lanza una excepción, Spring revierte la transacción
     * completa (rollback): ningún producto queda con el stock descontado si la
     * venta no se pudo registrar.
     *
     * Cada línea descuenta stock Y deja un movimiento VENTA (referencia
     * "VENTA #id"). Concurrencia: Producto tiene @Version; si otra operación
     * modificó el mismo producto entre tanto, el commit falla y se revierte
     * todo (la API responde 409).
     */
    @Transactional(rollbackFor = Exception.class)
    public Venta registrarVenta(VentaRequestDTO request) {

        validarSolicitud(request);

        // ---------- Paso 1: validar todo ANTES de modificar el stock ----------

        // Un mismo producto puede aparecer en varias líneas; se acumula lo
        // solicitado por producto para validar el stock contra el total.
        Map<Long, Producto> productos = new LinkedHashMap<>();
        Map<Long, Long> solicitado = new HashMap<>();

        for (ProductoVentaDTO item : request.getProductos()) {

            Producto producto = productos.computeIfAbsent(
                    item.getProductoId(), this::buscarProducto);

            validarProductoVendible(producto);

            // long para evitar desbordamiento al acumular cantidades
            long acumulado = solicitado.merge(
                    item.getProductoId(), (long) item.getCantidad(), Long::sum);

            int disponible = stockDisponible(producto);

            if (acumulado > disponible) {
                throw new ConflictoNegocioException(
                        "Stock insuficiente para " + producto.getNombre()
                                + ". Solicitado: " + acumulado
                                + ". Disponible: " + disponible);
            }
        }

        // ---------- Paso 2: construir la venta y descontar stock ----------

        Venta venta = new Venta();

        // Quién registra la venta (llave foránea a usuarios)
        venta.setUsuario(usuarioSesion.requerido());
        venta.setFechaVenta(LocalDateTime.now());

        List<DetalleVenta> detalles = new ArrayList<>();

        double totalVenta = 0;

        List<CambioStock> cambios = new ArrayList<>();

        for (ProductoVentaDTO item : request.getProductos()) {

            Producto producto = productos.get(item.getProductoId());

            int stockAntes = stockDisponible(producto);
            int stockDespues = stockAntes - item.getCantidad();

            double precio = producto.getPrecioVenta();
            double subtotal = precio * item.getCantidad();

            DetalleVenta detalle = new DetalleVenta();

            detalle.setProducto(producto);
            detalle.setVenta(venta);
            detalle.setCantidad(item.getCantidad());

            // Precio histórico: el del momento de la venta. El stock antes/después
            // queda en el movimiento de inventario, no se duplica aquí.
            detalle.setPrecioUnitario(precio);

            totalVenta += subtotal;

            // Descontar stock (el estado Agotado/Disponible se calcula a partir del stock)
            producto.setStock(stockDespues);

            productoRepository.save(producto);

            cambios.add(new CambioStock(producto, item.getCantidad(), stockAntes, stockDespues));

            detalles.add(detalle);
        }

        venta.setDetalles(detalles);
        venta.setTotal(totalVenta);

        Venta guardada = ventaRepository.save(venta);

        // Un movimiento VENTA por línea, ya con el id definitivo de la venta
        String referencia = "VENTA #" + guardada.getId();
        for (CambioStock c : cambios) {
            inventarioService.registrarMovimiento(c.producto(), TipoMovimiento.VENTA,
                    c.cantidad(), c.antes(), c.despues(), referencia, null);
        }

        return guardada;
    }

    /**
     * Cancela una venta COMPLETA (no se borra nada: la venta pasa a CANCELADA
     * y conserva su historial). Devuelve al inventario cada línea y registra
     * un movimiento DEVOLUCION por línea. Todo en una sola transacción.
     * Una venta ya cancelada no puede cancelarse otra vez (409).
     */
    @Transactional(rollbackFor = Exception.class)
    public Venta cancelarVenta(Long id, String motivo) {

        Venta venta = obtenerVentaPorId(id);

        if (venta.getEstado() == EstadoVenta.CANCELADA) {
            throw new ConflictoNegocioException(
                    "La venta " + id + " ya fue cancelada");
        }

        String referencia = "VENTA #" + venta.getId();
        String observacion = (motivo == null || motivo.isBlank())
                ? "Cancelación de venta"
                : "Cancelación de venta: " + motivo.trim();

        for (DetalleVenta detalle : venta.getDetalles()) {

            Producto producto = detalle.getProducto();
            int antes = InventarioService.stockDe(producto);
            long despues = (long) antes + detalle.getCantidad();

            if (despues > Integer.MAX_VALUE) {
                throw new ConflictoNegocioException(
                        "No se puede devolver el stock de " + producto.getNombre()
                                + ": superaría el máximo permitido");
            }

            inventarioService.aplicar(producto, TipoMovimiento.DEVOLUCION,
                    detalle.getCantidad(), (int) despues, referencia, observacion);
        }

        venta.setEstado(EstadoVenta.CANCELADA);
        venta.setFechaCancelacion(LocalDateTime.now());
        venta.setUsuarioCancelacion(usuarioSesion.requerido());
        venta.setMotivoCancelacion(motivo == null || motivo.isBlank() ? null : motivo.trim());

        return ventaRepository.save(venta);
    }

    // Obtener todas las ventas
    public List<Venta> listarVentas() {
        return ventaRepository.findAll();
    }

    // Buscar venta por ID (404 si no existe)
    public Venta obtenerVentaPorId(Long id) {

        return ventaRepository.findById(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Venta no encontrada con id " + id));
    }

    // ------------------------------------------------------------------
    // Validaciones privadas
    // ------------------------------------------------------------------

    // Segunda línea de defensa: el controller ya usa @Valid, pero el servicio
    // no debe asumir que siempre le llega una solicitud válida.
    private void validarSolicitud(VentaRequestDTO request) {

        if (request == null
                || request.getProductos() == null
                || request.getProductos().isEmpty()) {
            throw new SolicitudInvalidaException(
                    "La venta debe incluir al menos un producto");
        }

        for (ProductoVentaDTO item : request.getProductos()) {

            if (item == null || item.getProductoId() == null) {
                throw new SolicitudInvalidaException(
                        "Cada línea de venta debe indicar el productoId");
            }

            if (item.getCantidad() == null || item.getCantidad() <= 0) {
                throw new SolicitudInvalidaException(
                        "La cantidad debe ser mayor que cero (producto "
                                + item.getProductoId() + ")");
            }
        }
    }

    private Producto buscarProducto(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Producto no encontrado con id " + id));
    }

    private void validarProductoVendible(Producto producto) {

        if (!producto.estaActivo()) {
            throw new ConflictoNegocioException(
                    "No se puede vender el producto desactivado: "
                            + producto.getNombre());
        }

        // Vencido por la marca manual o porque su fecha de vencimiento ya pasó.
        if (producto.estaVencidoEfectivo()) {
            throw new ConflictoNegocioException(
                    "No se puede vender el producto vencido: "
                            + producto.getNombre());
        }

        Double precio = producto.getPrecioVenta();

        if (precio == null || precio.isNaN() || precio.isInfinite() || precio <= 0) {
            throw new ConflictoNegocioException(
                    "El producto " + producto.getNombre()
                            + " no tiene un precio de venta válido");
        }
    }

    // Un stock nulo o negativo en la BD se trata como 0 disponible
    private int stockDisponible(Producto producto) {
        Integer stock = producto.getStock();
        return stock == null ? 0 : Math.max(stock, 0);
    }
}
