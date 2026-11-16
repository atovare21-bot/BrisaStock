package com.brisastock.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "productos")
@Data
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 100)
    private String marca;

    /** Categoría del producto (tabla categorias). Puede no tener. */
    @ManyToOne
    @JoinColumn(name = "categoria_id", foreignKey = @ForeignKey(name = "fk_productos_categoria"))
    private Categoria categoria;

    @Column(length = 255)
    private String descripcion;

    // Dinero: decimal exacto en la base de datos (no punto flotante).
    @Min(value = 0, message = "El precio no puede ser negativo")
    @Column(nullable = false, columnDefinition = "decimal(12,2) not null")
    private Double precioCompra;

    @Min(value = 0, message = "El precio no puede ser negativo")
    @Column(nullable = false, columnDefinition = "decimal(12,2) not null")
    private Double precioVenta;

    @Min(value = 0, message = "El stock no puede ser negativo")
    @Column(nullable = false)
    private Integer stock;

    /**
     * Umbral de stock bajo de ESTE producto. Regla única en todo el sistema:
     * stock bajo  <=>  stock <= stockMinimo.
     */
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private Integer stockMinimo;

    /** Marca manual de vencido (p. ej. producto dañado antes de su fecha). */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private Boolean vencido;

    /** Fecha de vencimiento real (columna DATE). Nula = el producto no vence o no se registró. */
    private LocalDate fechaVencimiento;

    /**
     * Desactivación lógica. false = el producto no se puede vender ni aparece en
     * reportes/alertas, pero su fila, sus ventas y sus movimientos se conservan.
     */
    @Column(nullable = false, columnDefinition = "boolean not null default true")
    private Boolean activo = Boolean.TRUE;

    /**
     * Control de concurrencia optimista. Si dos transacciones modifican el
     * mismo producto a la vez, la segunda falla (-> HTTP 409) en vez de pisar
     * el stock de la primera.
     */
    @Version
    @JsonIgnore
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private Long version;

    /** Valores por defecto de las columnas obligatorias que el formulario deja opcionales. */
    @PrePersist
    @PreUpdate
    void completarValoresPorDefecto() {
        if (stock == null) stock = 0;
        if (stockMinimo == null) stockMinimo = 0;
        if (vencido == null) vencido = Boolean.FALSE;
        if (activo == null) activo = Boolean.TRUE;
    }

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

    /** Producto vendible/visible. */
    public boolean estaActivo() {
        return !Boolean.FALSE.equals(activo);
    }

    /** Vencido según la FECHA: la fecha existe y es anterior a hoy (sirve todo el día de su fecha). */
    @JsonProperty(value = "vencidoPorFecha", access = JsonProperty.Access.READ_ONLY)
    public boolean esVencidoPorFecha() {
        return fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now());
    }

    /** Vencido por la marca manual o por la fecha. */
    public boolean estaVencidoEfectivo() {
        return Boolean.TRUE.equals(vencido) || esVencidoPorFecha();
    }

    /** Sin unidades reales: stock nulo o <= 0. */
    public boolean estaAgotado() {
        return stock == null || stock <= 0;
    }

    /**
     * Estado del producto. Ya NO es una columna: se calcula a partir del stock y de
     * la marca de vencido, así nunca puede contradecir a los datos reales.
     * Se publica en el JSON como "estado" (solo lectura).
     */
    @JsonProperty(value = "estado", access = JsonProperty.Access.READ_ONLY)
    public String getEstado() {
        if (Boolean.TRUE.equals(vencido)) {
            return "Vencido";
        }
        return estaAgotado() ? "Agotado" : "Disponible";
    }
}
