package com.brisastock.security;

import com.brisastock.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escribe un {@link ApiError} directamente en la respuesta. Se usa en los
 * errores 401/403, que ocurren en la cadena de filtros de Spring Security
 * (antes de llegar a los controllers, por lo que @RestControllerAdvice no
 * participa).
 */
final class ErrorJsonWriter {

    private ErrorJsonWriter() {
    }

    static void escribir(ObjectMapper objectMapper, HttpServletResponse response,
                         HttpStatus status, String mensaje, String ruta) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(status, mensaje, ruta));
    }
}
