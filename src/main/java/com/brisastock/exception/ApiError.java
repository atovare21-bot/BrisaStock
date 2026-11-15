package com.brisastock.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * Formato único de error para toda la API (incluidos 401 y 403, que se
 * generan en la cadena de Spring Security y no en los controllers).
 *
 * Ejemplo:
 * {
 *   "status": 409,
 *   "error": "CONFLICT",
 *   "message": "Stock insuficiente para ...",
 *   "timestamp": "2026-10-07T22:30:00Z",
 *   "path": "/api/ventas"
 * }
 *
 * "errores" solo aparece en errores de validación (400) e indica campo y motivo.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        int status,
        String error,
        String message,
        Instant timestamp,
        String path,
        List<CampoError> errores) {

    public record CampoError(String campo, String mensaje) {
    }

    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(status.value(), status.name(), message, Instant.now(), path, null);
    }

    public static ApiError of(HttpStatus status, String message, String path, List<CampoError> errores) {
        return new ApiError(status.value(), status.name(), message, Instant.now(), path, errores);
    }
}
