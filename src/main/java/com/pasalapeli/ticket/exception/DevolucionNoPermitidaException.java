package com.pasalapeli.ticket.exception;

/**
 * La devolucion esta permitida solo hasta 1 hora antes del inicio de la funcion.
 */
public class DevolucionNoPermitidaException extends RuntimeException {

    public DevolucionNoPermitidaException(String message) {
        super(message);
    }
}
