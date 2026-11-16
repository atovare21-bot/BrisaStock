package com.brisastock.model;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Categoría de productos (Bebidas, Aseo, Lácteos...).
 *
 * Antes era un texto libre repetido en cada producto, lo que permitía escribir la
 * misma categoría de dos formas distintas. Ahora cada categoría existe una sola vez
 * y los productos la referencian con una llave foránea (productos.categoria_id).
 *
 * En el JSON una categoría se publica como su nombre (un texto), de modo que la
 * interfaz sigue recibiendo "categoria": "Bebidas".
 */
@Entity
@Table(name = "categorias",
        uniqueConstraints = @UniqueConstraint(name = "uk_categorias_nombre", columnNames = "nombre"))
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nombre;

    public Categoria() {
    }

    public Categoria(String nombre) {
        this.nombre = nombre;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @JsonValue
    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
}
