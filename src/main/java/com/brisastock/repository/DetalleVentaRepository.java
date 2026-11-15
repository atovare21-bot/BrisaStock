package com.brisastock.repository;

import com.brisastock.model.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetalleVentaRepository
        extends JpaRepository<DetalleVenta, Long> {

    // ¿El producto aparece en alguna venta? (la FK impide borrarlo)
    boolean existsByProductoId(Long productoId);
}
