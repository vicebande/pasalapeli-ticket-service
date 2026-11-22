package com.pasalapeli.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadResponseDTO {
    private Long funcionId;
    private Long peliculaId;
    private String peliculaTitulo;
    /** Genero de la pelicula: se usa como "categoria" en la metrica de ventas. */
    private String peliculaGenero;
    private String sala;
    private Integer entradasDisponibles;
    private BigDecimal precio;
    private boolean disponible;
    private LocalDate fecha;
    private LocalTime hora;
    private LocalDateTime finFuncion;
}
