package com.pasalapeli.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprarTicketRequestDTO {
    @NotNull(message = "El ID de usuario es obligatorio")
    private Long usuarioId;

    @NotNull(message = "El ID de función es obligatorio")
    private Long funcionId;

    @Min(value = 1, message = "Debe comprar al menos 1 entrada")
    @Builder.Default
    private int cantidad = 1;

    @NotBlank(message = "El método de pago es obligatorio")
    private String metodoPago; // ej. WEBPAY, TARJETA_CREDITO, TARJETA_DEBITO
}
