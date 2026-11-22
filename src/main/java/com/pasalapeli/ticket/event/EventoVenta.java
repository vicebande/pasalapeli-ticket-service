package com.pasalapeli.ticket.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de metricas de negocio publicado hacia Amazon Kinesis Data Streams
 * (stream-ventas) cada vez que una venta queda PAGADA. El BFF lo consume y
 * alimenta el dashboard administrativo en tiempo real (WebSocket STOMP).
 *
 * El payload es JSON estricto: el consumidor deserializa por nombre de campo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoVenta {

    /** VENTA_COMPLETADA */
    private String tipo;

    /** ID del ticket (identificador de la venta). */
    private Long idVenta;

    /** Fecha/hora ISO-8601 con offset de America/Santiago. */
    private String fecha;

    /** Monto total en pesos chilenos (entero, sin decimales). */
    private Long monto;

    /** Titulo de la pelicula (producto vendido). */
    private String producto;

    /** Genero de la pelicula (categoria del producto). */
    private String categoria;

    /** IP de origen del cliente (X-Forwarded-For o remoteAddr). */
    private String ipCliente;
}
