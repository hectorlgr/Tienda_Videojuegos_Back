package com.ferreteria.catalogo_service.exception;

import org.springframework.http.HttpStatus;

public class RawgException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public RawgException(HttpStatus status, String code, String message) {
        // No conservar causas externas: pueden contener la URL con la clave.
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
