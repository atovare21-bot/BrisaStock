package com.brisastock.support;

import com.brisastock.model.Producto;
import com.brisastock.model.Usuario;
import com.brisastock.repository.DetalleVentaRepository;
import com.brisastock.repository.MovimientoInventarioRepository;
import com.brisastock.repository.ProductoRepository;
import com.brisastock.repository.UsuarioRepository;
import com.brisastock.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de las pruebas de API: contexto completo + MockMvc + H2 aislada.
 * Antes de cada prueba deja la BD de pruebas en un estado conocido
 * (usuarios admin / empleado1 / cajero y algunos productos).
 *
 * No usa @Transactional a propósito: las pruebas de transacciones necesitan
 * que cada operación del servicio confirme o revierta de verdad.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = SoloBdAislada.class)
public abstract class PruebaApiBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected ProductoRepository productoRepository;
    @Autowired protected VentaRepository ventaRepository;
    @Autowired protected DetalleVentaRepository detalleVentaRepository;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected MovimientoInventarioRepository movimientoRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void prepararDatos() {
        movimientoRepository.deleteAll();   // primero: referencia a productos
        detalleVentaRepository.deleteAll();
        ventaRepository.deleteAll();
        productoRepository.deleteAll();
        usuarioRepository.deleteAll();

        crearUsuario("admin", "Admin12345", "ADMIN", true);
        crearUsuario("empleado1", "Empleado123", "EMPLEADO", true); // rol legado de la BD real
        crearUsuario("cajero", "Cajero12345", "USER", true);
        crearUsuario("inactivo", "Inactivo123", "USER", false);
    }

    protected Usuario crearUsuario(String username, String password, String rol, boolean activo) {
        Usuario u = new Usuario();
        u.setNombre(username);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setRol(rol);
        u.setActivo(activo);
        return usuarioRepository.save(u);
    }

    protected Producto crearProducto(String nombre, int stock, Double precioVenta, Boolean vencido) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setMarca("Marca");
        p.setCategoria("Bebidas");
        p.setPrecioCompra(1000.0);
        p.setPrecioVenta(precioVenta);
        p.setStock(stock);
        p.setStockMinimo(2);
        p.setVencido(vencido);
        p.setEstado(Boolean.TRUE.equals(vencido) ? "Vencido" : (stock == 0 ? "Agotado" : "Disponible"));
        return productoRepository.save(p);
    }

    protected int stockActual(Long productoId) {
        return productoRepository.findById(productoId).orElseThrow().getStock();
    }
}
