package com.brisastock.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * Reglas de la fecha de vencimiento (columna de texto libre en la BD).
 *
 * - Formato válido: AAAA-MM-DD, fecha real de calendario (2026-02-30 NO es válida).
 * - Los registros antiguos pueden tener otro formato: se tratan como "fecha
 *   desconocida". Nunca se convierten, nunca se sobrescriben y nunca bloquean
 *   por sí solos una venta.
 * - Un producto está vencido por fecha cuando la fecha es válida y es ANTERIOR a hoy
 *   (el producto sirve todo el día de su fecha de vencimiento).
 */
public final class FechaVencimiento {

    private static final DateTimeFormatter ISO_ESTRICTO =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private FechaVencimiento() {
    }

    /** Fecha real si el texto cumple AAAA-MM-DD estricto; vacío si es nulo, en blanco o ilegible. */
    public static Optional<LocalDate> interpretar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String limpio = texto.trim();
        if (limpio.length() != 10) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(limpio, ISO_ESTRICTO));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public static boolean esValida(String texto) {
        return interpretar(texto).isPresent();
    }

    /** true solo si la fecha es legible y anterior a {@code hoy}. Fecha ilegible => false (desconocida). */
    public static boolean estaVencida(String texto, LocalDate hoy) {
        return interpretar(texto).map(f -> f.isBefore(hoy)).orElse(false);
    }
}
