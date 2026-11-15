package com.brisastock.exception;

/**
 * El recurso solicitado (producto, venta, etc.) no existe.
 * Se traduce a HTTP 404 en {@link GlobalExceptionHandler}.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
