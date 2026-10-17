package com.pasalapeli.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadResponseDTO {
    private Long funcionId;
    private Long peliculaId;
    private String peliculaTitulo;
    private String sala;
    private Integer entradasDisponibles;
    private BigDecimal precio;
    private boolean disponible;
}
