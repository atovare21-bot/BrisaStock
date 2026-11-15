package com.brisastock.model;

import jakarta.persistence.*;
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
    private LocalDateTime fechaVenta;

    // Cliente genérico
    private String cliente;

    // Total de la venta
    private Double total;

    // ACTIVA / CANCELADA. DEFAULT 'ACTIVA' para que las ventas ya existentes
    // en la BD real queden activas al agregarse la columna (ddl-auto=update).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'ACTIVA'")
    private EstadoVenta estado = EstadoVenta.ACTIVA;

    // Datos de la cancelación (nulos mientras la venta esté ACTIVA)
    private LocalDateTime fechaCancelacion;

    @Column(length = 100)
    private String usuarioCancelacion;

    @Column(length = 500)
    private String motivoCancelacion;

    // Relación con detalle de venta
    @OneToMany(
            mappedBy = "venta",
            cascade = CascadeType.ALL
    )
    private List<DetalleVenta> detalles;
}