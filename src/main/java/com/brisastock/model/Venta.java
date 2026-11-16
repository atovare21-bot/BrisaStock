package com.brisastock.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ventas")
@Data
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Fecha y hora automática
    @Column(nullable = false)
    private LocalDateTime fechaVenta;

    /** Usuario que registró la venta (usuarios 1 — N ventas). En el JSON: su username. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ventas_usuario"))
    @JsonSerialize(using = UsernameSerializer.class)
    private Usuario usuario;

    // Total de la venta (decimal exacto)
    @Column(nullable = false, columnDefinition = "decimal(12,2) not null")
    private Double total;

    // ACTIVA / CANCELADA
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'ACTIVA'")
    private EstadoVenta estado = EstadoVenta.ACTIVA;

    // Datos de la cancelación (nulos mientras la venta esté ACTIVA)
    private LocalDateTime fechaCancelacion;

    /** Usuario que canceló la venta. En el JSON: su username. */
    @ManyToOne
    @JoinColumn(name = "usuario_cancelacion_id",
            foreignKey = @ForeignKey(name = "fk_ventas_usuario_cancelacion"))
    @JsonSerialize(using = UsernameSerializer.class)
    private Usuario usuarioCancelacion;

    @Column(length = 500)
    private String motivoCancelacion;

    // Líneas de la venta
    @OneToMany(
            mappedBy = "venta",
            cascade = CascadeType.ALL
    )
    private List<DetalleVenta> detalles;
}
