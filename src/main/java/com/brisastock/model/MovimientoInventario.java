package com.brisastock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro inmutable de un cambio de stock. Responde: ¿qué pasó con el stock
 * de este producto, cuándo y quién lo hizo?
 *
 * "cantidad" siempre es positiva (magnitud del cambio); el sentido del cambio
 * se ve en stockAnterior -> stockPosterior (y en el tipo).
 * "usuario" es el usuario responsable (llave foránea a usuarios): los usuarios
 * no se borran, solo se desactivan, así que el historial nunca queda huérfano.
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

    /** Usuario que hizo el movimiento (usuarios 1 — N movimientos). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_movimientos_usuario"))
    private Usuario usuario;

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

    @Column(length = 120)
    private String referencia;

    @Column(length = 500)
    private String observacion;
}
