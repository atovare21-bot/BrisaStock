package com.brisastock.repository;

import com.brisastock.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaRepository
        extends JpaRepository<Venta, Long> {

    List<Venta> findByFechaVentaGreaterThanEqual(LocalDateTime desde);

    List<Venta> findByFechaVentaLessThan(LocalDateTime hastaExclusiva);

    List<Venta> findByFechaVentaGreaterThanEqualAndFechaVentaLessThan(
            LocalDateTime desde, LocalDateTime hastaExclusiva);
}
