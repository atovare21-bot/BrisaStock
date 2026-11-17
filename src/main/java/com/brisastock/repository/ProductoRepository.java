package com.brisastock.repository;

import com.brisastock.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    @Query("select p from Producto p where "
            + "(:nombre is null or lower(p.nombre) like lower(concat('%', :nombre, '%'))) and "
            + "(:filtro = 'todos' or "
            + "(:filtro = 'bajo' and coalesce(p.stock, 0) <= coalesce(p.stockMinimo, 0)) or "
            + "(:filtro = 'agotado' and p.activo = true and coalesce(p.stock, 0) <= 0) or "
            + "(:filtro = 'vencido' and p.activo = true and "
            + "(p.vencido = true or p.fechaVencimiento < current_date)) or "
            + "(:filtro = 'inactivo' and p.activo = false))")
    Page<Producto> buscarPagina(@Param("nombre") String nombre,
                                @Param("filtro") String filtro,
                                Pageable pageable);

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    List<Producto> findByCategoriaNombreIgnoreCase(String categoria);


    List<Producto> findByStockLessThan(Integer stock);



    // Productos con stock bajo
List<Producto> findByStockLessThanEqual(Integer stock);

// Stock bajo según el stockMinimo de CADA producto: stock <= stockMinimo
// (stock o stockMinimo nulos cuentan como 0). Misma regla que Producto.esStockBajo().
// Los productos desactivados no generan alertas.
@Query("select p from Producto p where p.activo = true "
        + "and coalesce(p.stock, 0) <= coalesce(p.stockMinimo, 0)")
List<Producto> findConStockBajo();

// Agotados = sin unidades reales (stock nulo o <= 0), sin depender del texto de "estado".
@Query("select p from Producto p where p.activo = true and coalesce(p.stock, 0) <= 0")
List<Producto> findAgotados();

// Productos vencidos
List<Producto> findByVencido(Boolean vencido);

}
