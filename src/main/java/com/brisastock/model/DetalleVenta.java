package com.brisastock.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "detalle_ventas")
@Data
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Producto vendido
    @ManyToOne
    @JoinColumn(name = "producto_id")
private Producto producto;
private String nombreProducto;
private String marcaProducto;
private String categoriaProducto;
private Integer stockAntesVenta;
private Integer stockDespuesVenta;

    // Venta a la que pertenece
    @ManyToOne
@JoinColumn(name = "venta_id")
@JsonIgnore
private Venta venta;

    // Cantidad vendida
    private Integer cantidad;

    // Precio del producto EN ESE MOMENTO
    private Double precioUnitario;

    // Subtotal
    private Double subtotal;
}