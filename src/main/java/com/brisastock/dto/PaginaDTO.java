package com.brisastock.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Respuesta estable para los listados paginados de la API. */
public record PaginaDTO<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PaginaDTO<T> desde(Page<T> pagina) {
        return new PaginaDTO<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages(),
                pagina.isFirst(), pagina.isLast());
    }
}
