package com.brisastock.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401: la solicitud no trae credenciales o son incorrectas.
 * Mantiene el encabezado WWW-Authenticate para que clientes y navegadores
 * sepan que el esquema es HTTP Basic. El mensaje es deliberadamente genérico
 * (no revela si el usuario existe).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"BrisaStock\"");
        ErrorJsonWriter.escribir(objectMapper, response, HttpStatus.UNAUTHORIZED,
                "Autenticación requerida o credenciales inválidas", request.getRequestURI());
    }
}
