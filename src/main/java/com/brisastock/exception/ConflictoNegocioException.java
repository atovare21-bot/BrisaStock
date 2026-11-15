package com.brisastock.exception;

/**
 * La solicitud es válida pero entra en conflicto con el estado actual del
 * negocio (stock insuficiente, producto vencido, usuario duplicado, etc.).
 * Se traduce a HTTP 409 en {@link GlobalExceptionHandler}.
 */
public class ConflictoNegocioException extends RuntimeException {

    public ConflictoNegocioException(String mensaje) {
        super(mensaje);
    }
}
