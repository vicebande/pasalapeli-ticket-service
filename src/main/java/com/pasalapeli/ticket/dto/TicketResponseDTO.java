package com.pasalapeli.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponseDTO {
    private Long id;
    private String codigo;
    private String estado;
    private LocalDateTime fechaCompra;
    private Long usuarioId;
    private String usuarioNombre;
    private String usuarioCorreo;
    private Long funcionId;
    private String peliculaTitulo;
    private String sala;
    private int cantidad;
    private PagoDTO pago;
}
