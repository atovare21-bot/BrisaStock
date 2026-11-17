package com.brisastock.controller;

import com.brisastock.dto.ProductoActualizacionDTO;
import com.brisastock.dto.ProductoRequestDTO;
import com.brisastock.dto.PaginaDTO;
import com.brisastock.model.Producto;
import com.brisastock.service.ProductoService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    // Obtener todos los productos
    @GetMapping
    public List<Producto> listarProductos() {
        return productoService.listarProductos();
    }

    // Listado paginado para la interfaz; la ruta antigua conserva su respuesta en arreglo.
    @GetMapping("/pagina")
    public PaginaDTO<Producto> listarPagina(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(required = false) String nombre,
            @RequestParam(defaultValue = "todos") String filtro) {
        Pageable pageable = PageRequest.of(Math.max(0, pagina),
                Math.max(1, Math.min(tamano, 100)),
                Sort.by(Sort.Direction.ASC, "nombre").and(Sort.by("id")));
        return PaginaDTO.desde(productoService.listarPagina(nombre, filtro, pageable));
    }

    // Obtener un producto por ID
    @GetMapping("/{id}")
    public Producto obtenerProducto(@PathVariable Long id) {
        return productoService.obtenerProductoPorId(id);
    }

    // Crear producto
    @PostMapping
    public Producto crearProducto(@Valid @RequestBody ProductoRequestDTO producto) {
        return productoService.guardarProducto(producto);
    }

    // Actualizar datos administrativos (el stock se cambia en /api/inventario)
    @PutMapping("/{id}")
    public Producto actualizarProducto(
            @PathVariable Long id,
            @Valid @RequestBody ProductoActualizacionDTO producto) {

        return productoService.actualizarProducto(id, producto);
    }

    // Eliminar producto
    @DeleteMapping("/{id}")
    public String eliminarProducto(@PathVariable Long id) {

        productoService.eliminarProducto(id);

        return "Producto eliminado correctamente";
    }

    // Buscar producto por nombre
    @GetMapping("/buscar")
    public List<Producto> buscarPorNombre(
            @RequestParam String nombre) {

        return productoService.buscarPorNombre(nombre);
    }

    // Buscar por categoría
    @GetMapping("/categoria/{categoria}")
    public List<Producto> buscarPorCategoria(
            @PathVariable String categoria) {

        return productoService.buscarPorCategoria(categoria);
    }

    // Buscar por estado
    @GetMapping("/estado/{estado}")
    public List<Producto> buscarPorEstado(
            @PathVariable String estado) {

        return productoService.buscarPorEstado(estado);
    }

    // Productos con stock bajo: stock <= stockMinimo de cada producto
    @GetMapping("/stock-bajo")
    public List<Producto> stockBajo() {
        return productoService.stockBajo();
    }

    // Ruta antigua: el número ya NO se usa como umbral (una sola regla de stock bajo)
    @Deprecated
    @GetMapping("/stock-bajo/{stock}")
    public List<Producto> stockBajoRutaAntigua(@PathVariable Integer stock) {
        return productoService.stockBajo();
    }

    // Desactivación lógica (solo ADMIN: PATCH no es GET). No borra datos.
    @PatchMapping("/{id}/desactivar")
    public Producto desactivar(@PathVariable Long id) {
        return productoService.cambiarActivo(id, false);
    }

    @PatchMapping("/{id}/activar")
    public Producto activar(@PathVariable Long id) {
        return productoService.cambiarActivo(id, true);
    }

}
