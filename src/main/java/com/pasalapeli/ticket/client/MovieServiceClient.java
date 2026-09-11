package com.pasalapeli.ticket.client;

import com.pasalapeli.ticket.dto.DisponibilidadResponseDTO;
import com.pasalapeli.ticket.exception.InsufficientTicketsException;
import com.pasalapeli.ticket.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class MovieServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.movie-service.url:http://localhost:8082}")
    private String movieServiceUrl;

    public DisponibilidadResponseDTO consultarDisponibilidad(Long funcionId) {
        String url = String.format("%s/api/funciones/%d/disponibilidad", movieServiceUrl, funcionId);
        try {
            log.info("Consultando disponibilidad en Movie Service: {}", url);
            ResponseEntity<DisponibilidadResponseDTO> resp = restTemplate.getForEntity(url, DisponibilidadResponseDTO.class);
            if (resp.getBody() == null) {
                throw new IllegalStateException("Movie Service respondió sin cuerpo en disponibilidad para función: " + funcionId);
            }
            return resp.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Función no encontrada en Movie Service con ID: " + funcionId);
        } catch (Exception e) {
            log.error("Error al consultar disponibilidad en Movie Service: {}", e.getMessage());
            throw new RuntimeException("No fue posible comunicarse con Movie Service: " + e.getMessage());
        }
    }

    public void descontarEntradas(Long funcionId, int cantidad) {
        String url = String.format("%s/api/funciones/%d/descontar?cantidad=%d", movieServiceUrl, funcionId, cantidad);
        try {
            log.info("Solicitando descuento de {} entradas en Movie Service: {}", cantidad, url);
            restTemplate.exchange(url, HttpMethod.PUT, null, DisponibilidadResponseDTO.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // Movie Service retornó 409
                throw new InsufficientTicketsException("No hay entradas disponibles suficientes para completar la compra.");
            } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Función no encontrada para descontar entradas.");
            }
            throw new RuntimeException("Error en Movie Service al descontar entradas: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error al descontar entradas en Movie Service: {}", e.getMessage());
            throw new RuntimeException("Fallo al actualizar disponibilidad en Movie Service: " + e.getMessage());
        }
    }
}
