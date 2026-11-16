package com.brisastock.repository;

import com.brisastock.model.MovimientoInventario;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoInventarioRepository
        extends JpaRepository<MovimientoInventario, Long> {

    @EntityGraph(attributePaths = {"producto", "usuario"})
    List<MovimientoInventario> findAllByOrderByFechaDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"producto", "usuario"})
    List<MovimientoInventario> findByProductoIdOrderByFechaDescIdDesc(Long productoId, Pageable pageable);

    boolean existsByProductoId(Long productoId);
}
