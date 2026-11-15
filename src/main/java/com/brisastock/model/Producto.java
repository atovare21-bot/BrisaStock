package com.brisastock.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Entity
@Table(name = "productos")
@Data
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String marca;

    private String categoria;

    private String descripcion;

    @Min(value = 0, message = "El precio no puede ser negativo")
    private Double precioCompra;

    @Min(value = 0, message = "El precio no puede ser negativo")
    private Double precioVenta;

    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;

    /**
     * Umbral de stock bajo de ESTE producto. Regla única en todo el sistema:
     * stock bajo  <=>  stock <= stockMinimo   (stockMinimo nulo se trata como 0).
     */
    private Integer stockMinimo;

    private String estado;

    private Boolean vencido;

    private String fechaVencimiento;

    /**
     * Desactivación lógica. false = el producto no se puede vender ni aparece en
     * reportes/alertas, pero su fila, sus ventas y sus movimientos se conservan.
     * El DEFAULT true permite que "ddl-auto=update" agregue la columna a la tabla
     * real dejando ACTIVOS a todos los productos existentes (cambio solo aditivo).
     */
    @Column(nullable = false, columnDefinition = "boolean not null default true")
    private Boolean activo = Boolean.TRUE;

    /**
     * Control de concurrencia optimista. Si dos transacciones modifican el
     * mismo producto a la vez, la segunda falla (-> HTTP 409) en vez de pisar
     * el stock de la primera.
     * El DEFAULT 0 permite que "ddl-auto=update" agregue la columna a la tabla
     * real sin dejar NULL en los productos existentes.
     */
    @Version
    @JsonIgnore
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private Long version;

    /**
     * ¿Está en stock bajo según su propio stockMinimo? Se publica en el JSON como
     * "stockBajo" (solo lectura) para que los clientes no dupliquen la regla.
     */
    @JsonProperty(value = "stockBajo", access = JsonProperty.Access.READ_ONLY)
    public boolean esStockBajo() {
        int actual = stock == null ? 0 : stock;
        int minimo = stockMinimo == null ? 0 : stockMinimo;
        return actual <= minimo;
    }

    /** Producto vendible/visible. Un valor nulo (dato ajeno a JPA) cuenta como activo. */
    public boolean estaActivo() {
        return !Boolean.FALSE.equals(activo);
    }

    /**
     * Vencido según la FECHA (válida AAAA-MM-DD anterior a hoy). Independiente del
     * indicador manual "vencido". Fechas con formato antiguo => false (desconocido).
     */
    @JsonProperty(value = "vencidoPorFecha", access = JsonProperty.Access.READ_ONLY)
    public boolean esVencidoPorFecha() {
        return FechaVencimiento.estaVencida(fechaVencimiento, java.time.LocalDate.now());
    }

    /** Vencido por indicador manual, por estado heredado "Vencido" o por fecha. */
    public boolean estaVencidoEfectivo() {
        return Boolean.TRUE.equals(vencido)
                || "Vencido".equalsIgnoreCase(estado)
                || esVencidoPorFecha();
    }

    /** Sin unidades reales: stock nulo o <= 0 (no depende del texto de "estado"). */
    public boolean estaAgotado() {
        return stock == null || stock <= 0;
    }
}
