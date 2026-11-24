package com.ferreteria.venta_service.exception;

/** Error de transporte validado antes de invocar el coordinador. */
public class CheckoutHttpException extends RuntimeException {
    private final int status;
    private final String codigo;

    public CheckoutHttpException(int status, String codigo) {
        super(codigo);
        this.status = status;
        this.codigo = codigo;
    }

    public int getStatus() { return status; }
    public String getCodigo() { return codigo; }
}
