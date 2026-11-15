package com.brisastock.exception;

/**
 * Los datos recibidos no son válidos (validación de negocio dentro del
 * Service, como segunda línea de defensa tras Bean Validation).
 * Se traduce a HTTP 400 en {@link GlobalExceptionHandler}.
 */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
