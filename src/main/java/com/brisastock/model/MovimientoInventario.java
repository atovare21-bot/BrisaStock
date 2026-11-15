package com.brisastock.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro inmutable de un cambio de stock. Responde: ¿qué pasó con el stock
 * de este producto, cuándo y quién lo hizo?
 *
 * "cantidad" siempre es positiva (magnitud del cambio); el sentido del cambio
 * se ve en stockAnterior -> stockPosterior (y en el tipo).
 * "usuario" se guarda como texto (username) para que el historial no dependa
 * de que el usuario siga existiendo.
 */
@Entity
@Table(name = "movimientos_inventario",
        indexes = @Index(name = "idx_mov_producto_fecha", columnList = "producto_id, fecha"))
@Getter
@Setter
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipo;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false)
    private Integer stockAnterior;

    @Column(nullable = false)
    private Integer stockPosterior;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false, length = 100)
    private String usuario;

    @Column(length = 120)
    private String referencia;

    @Column(length = 500)
    private String observacion;
}
