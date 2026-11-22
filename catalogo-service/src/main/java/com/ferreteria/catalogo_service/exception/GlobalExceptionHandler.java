package com.ferreteria.catalogo_service.exception;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleImagenGrande(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE,
                "Máximo 5 MiB por archivo y 6 MiB por solicitud multipart.", request.getRequestURI());
    }

    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<ApiErrorResponse> handleMultipart(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Envíe una imagen mediante multipart/form-data en el campo archivo.", request.getRequestURI());
    }

    @ExceptionHandler(ImagenStorageException.class)
    public ResponseEntity<ApiErrorResponse> handleImagenStorage(ImagenStorageException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(RawgException.class)
    public ResponseEntity<java.util.Map<String, Object>> handleRawg(RawgException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(java.util.Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", ex.getStatus().value(),
                "error", ex.getStatus().getReasonPhrase(),
                "code", ex.getCode(),
                "message", ex.getMessage(),
                "path", request.getRequestURI()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, LockTimeoutException.class,
            PessimisticLockException.class})
    public ResponseEntity<ApiErrorResponse> handleLockConflict(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT,
                "El producto está siendo modificado. Consulte su estado antes de reintentar.",
                request.getRequestURI());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Datos inválidos: revise SKU, campos obligatorios de categoría, longitudes, URL de imágenes, precio, oferta, stock, stockCritico, stockEsperado y cantidad.",
                request.getRequestURI());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleFormat(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Formato inválido: revise el JSON, los números enteros y los valores de categoría.",
                request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrity(Exception ex, HttpServletRequest request) {
        // Identificar solo la restricción de SKU; no exponer mensajes SQL al cliente.
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(java.util.Locale.ROOT)
                            .contains("uq_producto_variante_rawg")) {
                return buildResponse(HttpStatus.CONFLICT,
                        "Ya existe esta variante RAWG, plataforma y formato.", request.getRequestURI());
            }
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(java.util.Locale.ROOT)
                            .contains("uq_producto_codigo")) {
                return buildResponse(HttpStatus.CONFLICT,
                        "El código SKU ya pertenece a otro producto.", request.getRequestURI());
            }
        }
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Los datos no cumplen las restricciones del producto.", request.getRequestURI());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleWebClientResponse(WebClientResponseException ex,
            HttpServletRequest request) {
        HttpStatus status = (HttpStatus) ex.getStatusCode();
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return buildResponse(status, "Error de comunicación con servicio externo.",
                request.getRequestURI());
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleWebClientRequest(WebClientRequestException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_GATEWAY,
                "No se pudo establecer conexión con el servicio externo.",
                request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado.", request.getRequestURI());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String message, String path) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        return new ResponseEntity<>(error, status);
    }
}
