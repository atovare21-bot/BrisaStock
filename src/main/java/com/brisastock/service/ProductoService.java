package com.brisastock.service;

import com.brisastock.dto.ProductoActualizacionDTO;
import com.brisastock.dto.ProductoRequestDTO;
import com.brisastock.exception.ConflictoNegocioException;
import com.brisastock.exception.RecursoNoEncontradoException;
import com.brisastock.exception.SolicitudInvalidaException;
import com.brisastock.model.Categoria;
import com.brisastock.model.FechaVencimiento;
import com.brisastock.model.Producto;
import com.brisastock.model.TipoMovimiento;
import com.brisastock.repository.CategoriaRepository;
import com.brisastock.repository.DetalleVentaRepository;
import com.brisastock.repository.MovimientoInventarioRepository;
import com.brisastock.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private DetalleVentaRepository detalleVentaRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    // Listar todos los productos
    public List<Producto> listarProductos() {
        return productoRepository.findAll();
    }

    // Consultar un producto por ID (404 si no existe)
    public Producto obtenerProductoPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Producto no encontrado con id " + id));
    }

    // Guardar producto. Si nace con stock, ese stock inicial queda registrado
    // como un movimiento ENTRADA (stock anterior 0) para que el historial
    // explique TODO el stock del producto desde su primer día.
    @Transactional
    public Producto guardarProducto(ProductoRequestDTO datos) {

        Producto producto = new Producto();
        aplicarDatos(producto, datos);

        Producto guardado = productoRepository.save(producto);

        int stockInicial = guardado.getStock() == null ? 0 : guardado.getStock();
        if (stockInicial > 0) {
            inventarioService.registrarMovimiento(guardado, TipoMovimiento.ENTRADA,
                    stockInicial, 0, stockInicial, "STOCK_INICIAL",
                    "Stock inicial al crear el producto");
        }

        return guardado;
    }

    // Actualizar datos ADMINISTRATIVOS del producto. NO modifica el stock:
    // para eso existen las operaciones de /api/inventario (con trazabilidad).
    @Transactional
    public Producto actualizarProducto(Long id, ProductoActualizacionDTO datos) {

        Producto producto = obtenerProductoPorId(id);

        int stockActual = producto.getStock() == null ? 0 : producto.getStock();
        if (datos.stock() != null && datos.stock() != stockActual) {
            throw new SolicitudInvalidaException(
                    "El stock no se modifica con PUT /api/productos. Use "
                            + "/api/inventario/entradas, /salidas o /ajustes");
        }

        // Fecha: si viene distinta de la guardada debe ser AAAA-MM-DD real. Si es igual
        // a la guardada (p. ej. un formato antiguo) se tolera para poder editar el resto.
        String fechaNueva = datos.fechaVencimiento();
        validarFecha(fechaNueva);

        producto.setNombre(datos.nombre().trim());
        // Campos opcionales: null (omitido) = conservar lo guardado; "" = borrar a propósito.
        if (datos.marca() != null) producto.setMarca(vacioANull(datos.marca()));
        if (datos.categoria() != null) producto.setCategoria(resolverCategoria(datos.categoria()));
        if (datos.descripcion() != null) producto.setDescripcion(vacioANull(datos.descripcion()));
        producto.setPrecioCompra(datos.precioCompra());
        producto.setPrecioVenta(datos.precioVenta());
        if (datos.stockMinimo() != null) producto.setStockMinimo(datos.stockMinimo());
        if (datos.vencido() != null) producto.setVencido(datos.vencido());
        if (fechaNueva != null) producto.setFechaVencimiento(FechaVencimiento.interpretar(fechaNueva).orElse(null));

        return productoRepository.save(producto);
    }

    // Eliminar producto
    @Transactional
    public void eliminarProducto(Long id) {

        if (!productoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException(
                    "Producto no encontrado con id " + id);
        }

        // La FK detalle_ventas.producto_id impide borrar productos ya vendidos.
        // Se informa con un 409 claro en lugar de un error 500 de base de datos.
        if (detalleVentaRepository.existsByProductoId(id)) {
            throw new ConflictoNegocioException(
                    "No se puede eliminar el producto porque tiene ventas registradas");
        }

        // Un producto con historial de inventario no se borra: se perdería la trazabilidad.
        if (movimientoRepository.existsByProductoId(id)) {
            throw new ConflictoNegocioException(
                    "No se puede eliminar el producto porque tiene movimientos de inventario registrados");
        }

        productoRepository.deleteById(id);
    }

    // Buscar productos por nombre
    public List<Producto> buscarPorNombre(String nombre) {
        return productoRepository
                .findByNombreContainingIgnoreCase(nombre);
    }

    // Buscar por categoría
    public List<Producto> buscarPorCategoria(String categoria) {
        return productoRepository.findByCategoriaNombreIgnoreCase(categoria);
    }

    // Buscar por estado
    public List<Producto> buscarPorEstado(String estado) {
        // El estado ya no es una columna: se calcula por producto
        return productoRepository.findAll().stream()
                .filter(p -> p.getEstado().equalsIgnoreCase(estado))
                .toList();
    }

    // Stock bajo con la regla única: stock <= stockMinimo de cada producto
    public List<Producto> stockBajo() {
        return productoRepository.findConStockBajo();
    }

    // Desactivación lógica: no borra nada, solo oculta el producto de nuevas ventas y alertas
    @Transactional
    public Producto cambiarActivo(Long id, boolean activo) {
        Producto producto = obtenerProductoPorId(id);
        producto.setActivo(activo);
        return productoRepository.save(producto);
    }

    // Copia los datos validados del DTO a la entidad y recalcula el estado
    private void aplicarDatos(Producto producto, ProductoRequestDTO datos) {

        producto.setNombre(datos.nombre().trim());
        producto.setMarca(vacioANull(datos.marca()));
        producto.setCategoria(resolverCategoria(datos.categoria()));
        producto.setDescripcion(vacioANull(datos.descripcion()));
        producto.setPrecioCompra(datos.precioCompra());
        producto.setPrecioVenta(datos.precioVenta());
        producto.setStock(datos.stock());
        producto.setStockMinimo(datos.stockMinimo());
        producto.setVencido(datos.vencido());
        validarFecha(datos.fechaVencimiento());
        producto.setFechaVencimiento(FechaVencimiento.interpretar(datos.fechaVencimiento()).orElse(null));
    }

    // Una fecha enviada por la API debe ser AAAA-MM-DD y existir en el calendario
    private void validarFecha(String fecha) {
        if (fecha != null && !fecha.isBlank() && !FechaVencimiento.esValida(fecha)) {
            throw new SolicitudInvalidaException(
                    "La fecha de vencimiento debe tener formato AAAA-MM-DD y ser una fecha real");
        }
    }

    /**
     * Devuelve la categoría con ese nombre; si no existe todavía, la crea.
     * Texto vacío = el producto queda sin categoría.
     */
    private Categoria resolverCategoria(String nombre) {
        String limpio = vacioANull(nombre);
        if (limpio == null) {
            return null;
        }
        return categoriaRepository.findByNombreIgnoreCase(limpio)
                .orElseGet(() -> categoriaRepository.save(new Categoria(limpio)));
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}

