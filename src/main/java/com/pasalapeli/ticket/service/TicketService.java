package com.pasalapeli.ticket.service;

import com.pasalapeli.ticket.client.MovieServiceClient;
import com.pasalapeli.ticket.dto.ComprarTicketRequestDTO;
import com.pasalapeli.ticket.dto.DisponibilidadResponseDTO;
import com.pasalapeli.ticket.dto.PagoDTO;
import com.pasalapeli.ticket.dto.TicketResponseDTO;
import com.pasalapeli.ticket.entity.*;
import com.pasalapeli.ticket.exception.InsufficientTicketsException;
import com.pasalapeli.ticket.exception.ResourceNotFoundException;
import com.pasalapeli.ticket.repository.PagoRepository;
import com.pasalapeli.ticket.repository.TicketRepository;
import com.pasalapeli.ticket.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovieServiceClient movieServiceClient;

    @Transactional
    public TicketResponseDTO procesarCompra(ComprarTicketRequestDTO request) {
        log.info("Iniciando compra de ticket para usuario ID: {} en función ID: {}",
                request.getUsuarioId(), request.getFuncionId());

        // 1. Validar existencia del usuario
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + request.getUsuarioId()));

        // 2. Consultar disponibilidad en tiempo real con Movie Service (Flujo de secuencia)
        DisponibilidadResponseDTO disponibilidad = movieServiceClient.consultarDisponibilidad(request.getFuncionId());

        if (!disponibilidad.isDisponible() || disponibilidad.getEntradasDisponibles() < request.getCantidad()) {
            log.warn("Compra rechazada (409 Conflict): Entradas insuficientes para función ID: {}", request.getFuncionId());
            throw new InsufficientTicketsException(
                    String.format("Conflicto de disponibilidad: Solicitadas %d, disponibles %d",
                            request.getCantidad(), disponibilidad.getEntradasDisponibles())
            );
        }

        // 3. Descontar stock en Movie Service
        movieServiceClient.descontarEntradas(request.getFuncionId(), request.getCantidad());

        // 4. Generar código único de ticket
        String codigoTicket = "PLP-" + LocalDateTime.now().getYear() + "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 5. Crear y guardar Ticket
        Ticket ticket = Ticket.builder()
                .fechaCompra(LocalDateTime.now())
                .codigo(codigoTicket)
                .estado(EstadoTicket.PAGADO)
                .usuario(usuario)
                .funcionId(request.getFuncionId())
                .build();

        Ticket ticketGuardado = ticketRepository.save(ticket);

        // 6. Procesar pago simulado
        BigDecimal montoUnitario = disponibilidad.getPrecio() != null ? disponibilidad.getPrecio() : BigDecimal.ZERO;
        BigDecimal montoTotal = montoUnitario.multiply(BigDecimal.valueOf(request.getCantidad()));

        Pago pago = Pago.builder()
                .monto(montoTotal)
                .metodo(request.getMetodoPago().toUpperCase())
                .estado(EstadoPago.APROBADO)
                .fechaPago(LocalDateTime.now())
                .ticket(ticketGuardado)
                .build();

        Pago pagoGuardado = pagoRepository.save(pago);
        ticketGuardado.setPago(pagoGuardado);

        log.info("Ticket emitido exitosamente. Código: {}, Pago ID: {}", codigoTicket, pagoGuardado.getId());

        return mapToDTO(ticketGuardado, disponibilidad.getPeliculaTitulo(), disponibilidad.getSala(), request.getCantidad());
    }

    @Transactional(readOnly = true)
    public TicketResponseDTO obtenerPorId(Long id) {
        Ticket t = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado con ID: " + id));
        DisponibilidadResponseDTO disp = movieServiceClient.consultarDisponibilidad(t.getFuncionId());
        return mapToDTO(t, disp.getPeliculaTitulo(), disp.getSala(), 1);
    }

    @Transactional(readOnly = true)
    public TicketResponseDTO obtenerPorCodigo(String codigo) {
        Ticket t = ticketRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado con código: " + codigo));
        DisponibilidadResponseDTO disp = movieServiceClient.consultarDisponibilidad(t.getFuncionId());
        return mapToDTO(t, disp.getPeliculaTitulo(), disp.getSala(), 1);
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> listarPorUsuario(Long usuarioId) {
        return ticketRepository.findByUsuarioIdOrderByFechaCompraDesc(usuarioId).stream()
                .map(t -> {
                    try {
                        DisponibilidadResponseDTO disp = movieServiceClient.consultarDisponibilidad(t.getFuncionId());
                        return mapToDTO(t, disp.getPeliculaTitulo(), disp.getSala(), 1);
                    } catch (Exception e) {
                        return mapToDTO(t, "Película", "Sala", 1);
                    }
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> listarTodos() {
        return ticketRepository.findAll().stream()
                .map(t -> mapToDTO(t, "Película", "Sala", 1))
                .collect(Collectors.toList());
    }

    private TicketResponseDTO mapToDTO(Ticket t, String peliculaTitulo, String sala, int cantidad) {
        PagoDTO pagoDto = null;
        if (t.getPago() != null) {
            pagoDto = PagoDTO.builder()
                    .id(t.getPago().getId())
                    .monto(t.getPago().getMonto())
                    .metodo(t.getPago().getMetodo())
                    .estado(t.getPago().getEstado().name())
                    .fechaPago(t.getPago().getFechaPago())
                    .build();
        }

        return TicketResponseDTO.builder()
                .id(t.getId())
                .codigo(t.getCodigo())
                .estado(t.getEstado().name())
                .fechaCompra(t.getFechaCompra())
                .usuarioId(t.getUsuario() != null ? t.getUsuario().getId() : null)
                .usuarioNombre(t.getUsuario() != null ? t.getUsuario().getNombre() : null)
                .usuarioCorreo(t.getUsuario() != null ? t.getUsuario().getCorreo() : null)
                .funcionId(t.getFuncionId())
                .peliculaTitulo(peliculaTitulo)
                .sala(sala)
                .cantidad(cantidad)
                .pago(pagoDto)
                .build();
    }
}
