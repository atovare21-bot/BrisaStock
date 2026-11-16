package com.brisastock.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Línea de una venta: qué producto, cuántas unidades y a qué precio se vendió.
 *
 * Es la tabla que resuelve la relación muchos a muchos entre ventas y productos.
 * Solo guarda lo que no se puede obtener de otra tabla:
 *   - la cantidad vendida, y
 *   - el precio unitario EN ESE MOMENTO (el precio del producto puede cambiar después).
 *
 * El nombre del producto se lee del producto relacionado, el subtotal se calcula
 * (cantidad × precio) y el stock antes/después queda en movimientos_inventario,
 * así que esas columnas duplicadas se eliminaron.
 */
@Entity
@Table(name = "detalle_ventas")
@Data
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Venta a la que pertenece
    @ManyToOne(optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    @JsonIgnore
    private Venta venta;

    // Producto vendido
    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    // Cantidad vendida
    @Column(nullable = false)
    private Integer cantidad;

    // Precio del producto EN ESE MOMENTO (decimal exacto)
    @Column(nullable = false, columnDefinition = "decimal(12,2) not null")
    private Double precioUnitario;

    /** Nombre del producto vendido (se lee del producto; no se duplica en esta tabla). */
    @JsonProperty(value = "nombreProducto", access = JsonProperty.Access.READ_ONLY)
    public String getNombreProducto() {
        return producto == null ? null : producto.getNombre();
    }

    /** Subtotal de la línea = cantidad × precio unitario (calculado; no es una columna). */
    @JsonProperty(value = "subtotal", access = JsonProperty.Access.READ_ONLY)
    public Double getSubtotal() {
        if (cantidad == null || precioUnitario == null) {
            return null;
        }
        return cantidad * precioUnitario;
    }
}
