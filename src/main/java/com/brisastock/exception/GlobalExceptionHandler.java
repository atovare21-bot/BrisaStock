package com.brisastock.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Manejo global de errores de la API. Todas las respuestas de error usan el
 * formato {@link ApiError}.
 *
 * Mapa de códigos:
 *   400  datos inválidos (Bean Validation, JSON mal formado, parámetros)
 *   401  no autenticado          (lo emite RestAuthenticationEntryPoint)
 *   403  sin permisos            (lo emite RestAccessDeniedHandler)
 *   404  recurso inexistente
 *   409  conflicto de negocio (stock insuficiente, duplicados, producto vencido,
 *        venta ya cancelada) o de concurrencia (modificación simultánea)
 *   500  error inesperado (se registra en el log; al cliente solo llega un
 *        mensaje genérico, nunca stack traces ni detalles internos)
 *
 * Extiende ResponseEntityExceptionHandler para cubrir también las excepciones
 * estándar de Spring MVC (405, 415, parámetros faltantes, etc.).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ------------------------------------------------------------------
    // Excepciones propias de la aplicación
    // ------------------------------------------------------------------

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> manejarNoEncontrado(RecursoNoEncontradoException ex,
                                                        HttpServletRequest request) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(ConflictoNegocioException.class)
    public ResponseEntity<ApiError> manejarConflicto(ConflictoNegocioException ex,
                                                     HttpServletRequest request) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ApiError> manejarSolicitudInvalida(SolicitudInvalidaException ex,
                                                             HttpServletRequest request) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), null);
    }

    // ------------------------------------------------------------------
    // Validación y persistencia
    // ------------------------------------------------------------------

    /** Validación Bean Validation disparada al persistir (red de seguridad). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> manejarViolaciones(ConstraintViolationException ex,
                                                       HttpServletRequest request) {
        List<ApiError.CampoError> errores = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.CampoError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, "Datos inválidos", request.getRequestURI(), errores);
    }

    /** Violación de FK/unicidad que no se detectó antes (p. ej. condición de carrera). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> manejarIntegridad(DataIntegrityViolationException ex,
                                                      HttpServletRequest request) {
        log.warn("Violación de integridad de datos en {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.CONFLICT,
                "La operación entra en conflicto con datos existentes", request.getRequestURI(), null);
    }

    /**
     * Conflicto de concurrencia (@Version): otra operación modificó el mismo
     * producto/venta entre la lectura y el guardado. La transacción ya se
     * revirtió completa; el cliente puede reintentar.
     */
    @ExceptionHandler({OptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<ApiError> manejarConcurrencia(Exception ex, HttpServletRequest request) {
        log.warn("Conflicto de concurrencia en {} {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage());
        return respuesta(HttpStatus.CONFLICT,
                "Otra operación modificó el inventario al mismo tiempo; la operación se canceló, vuelve a intentarlo",
                request.getRequestURI(), null);
    }

    // ------------------------------------------------------------------
    // Seguridad (red de seguridad si algún día se usa @PreAuthorize)
    // ------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> manejarAccesoDenegado(AccessDeniedException ex,
                                                          HttpServletRequest request) {
        return respuesta(HttpStatus.FORBIDDEN,
                "No tienes permisos para realizar esta operación", request.getRequestURI(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> manejarAutenticacion(AuthenticationException ex,
                                                         HttpServletRequest request) {
        return respuesta(HttpStatus.UNAUTHORIZED,
                "Autenticación requerida o credenciales inválidas", request.getRequestURI(), null);
    }

    // ------------------------------------------------------------------
    // Cualquier otra excepción: 500 genérico
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> manejarInesperado(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno del servidor", request.getRequestURI(), null);
    }

    // ------------------------------------------------------------------
    // Excepciones estándar de Spring MVC (sobrescritas para usar ApiError
    // y para no filtrar detalles internos de Jackson/Spring)
    // ------------------------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiError.CampoError> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.CampoError(e.getField(), e.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST, "Datos inválidos", ruta(request), errores);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud falta, está mal formado o contiene valores inválidos",
                ruta(request));
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST,
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'", ruta(request));
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request) {
        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST,
                "Valor inválido para el parámetro '" + ex.getPropertyName() + "'", ruta(request));
        return ResponseEntity.badRequest().body(body);
    }

    /** Resto de excepciones estándar de MVC (404 de ruta, 405, 406, 415...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        if (status.is5xxServerError()) {
            log.error("Error de infraestructura MVC en {}", ruta(request), ex);
        }
        ApiError error = ApiError.of(status, mensajeGenerico(status), ruta(request));
        return ResponseEntity.status(statusCode).headers(headers).body(error);
    }

    // ------------------------------------------------------------------

    private static ResponseEntity<ApiError> respuesta(HttpStatus status, String mensaje, String ruta,
                                                      List<ApiError.CampoError> errores) {
        return ResponseEntity.status(status).body(ApiError.of(status, mensaje, ruta, errores));
    }

    private static String ruta(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
    }

    private static String mensajeGenerico(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "Solicitud inválida";
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método HTTP no permitido para este recurso";
            case 406 -> "Formato de respuesta no aceptable";
            case 415 -> "Tipo de contenido no soportado";
            default -> status.is5xxServerError() ? "Error interno del servidor" : status.getReasonPhrase();
        };
    }
}
