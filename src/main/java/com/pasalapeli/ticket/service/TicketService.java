package com.pasalapeli.ticket.service;

import com.pasalapeli.ticket.client.MovieServiceClient;
import com.pasalapeli.ticket.dto.ComprarTicketRequestDTO;
import com.pasalapeli.ticket.dto.DisponibilidadResponseDTO;
import com.pasalapeli.ticket.dto.PagoDTO;
import com.pasalapeli.ticket.dto.TicketResponseDTO;
import com.pasalapeli.ticket.entity.*;
import com.pasalapeli.ticket.event.EventoNotificacion;
import com.pasalapeli.ticket.exception.DevolucionNoPermitidaException;
import com.pasalapeli.ticket.exception.InsufficientTicketsException;
import com.pasalapeli.ticket.exception.ResourceNotFoundException;
import com.pasalapeli.ticket.exception.TicketYaDevueltoException;
import com.pasalapeli.ticket.messaging.NotificacionEventPublisher;
import com.pasalapeli.ticket.repository.PagoRepository;
import com.pasalapeli.ticket.repository.TicketRepository;
import com.pasalapeli.ticket.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final NotificacionEventPublisher notificacionEventPublisher;

    /**
     * Verifica que el usuario autenticado (correo/principal) pueda operar sobre los tickets
     * de usuarioId, o que sea ADMIN. Lanza 401/403/404 segun corresponda.
     */
    public void verificarAccesoAUsuario(Authentication authentication, Long usuarioId) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacion requerida");
        }
        boolean esAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (esAdmin) {
            return;
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + usuarioId));
        if (!authentication.getName().equalsIgnoreCase(usuario.getCorreo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para acceder a los tickets de este usuario");
        }
    }

    @Transactional
    public TicketResponseDTO procesarCompra(ComprarTicketRequestDTO request, String idempotencyKey) {
        log.info("Iniciando compra de ticket para usuario ID: {} en función ID: {}",
                request.getUsuarioId(), request.getFuncionId());

        // 1. Validar existencia del usuario
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + request.getUsuarioId()));

        // 2. Idempotencia opcional: si ya existe un ticket con esta clave, devolverlo sin descontar stock
        String claveNormalizada = normalizarClave(idempotencyKey);
        if (claveNormalizada != null) {
            Optional<Ticket> previo = ticketRepository
                    .findByUsuarioIdAndFuncionIdAndIdempotencyKey(request.getUsuarioId(), request.getFuncionId(), claveNormalizada);
            if (previo.isPresent()) {
                log.info("Replay idempotente: ticket {} ya existe para la clave {}. No se descuenta stock.",
                        previo.get().getCodigo(), claveNormalizada);
                DisponibilidadResponseDTO disp = movieServiceClient.consultarDisponibilidad(request.getFuncionId());
                return mapToDTO(previo.get(), disp.getPeliculaTitulo(), disp.getSala());
            }
        }

        // 3. Consultar disponibilidad en tiempo real con Movie Service (Flujo de secuencia)
        DisponibilidadResponseDTO disponibilidad = movieServiceClient.consultarDisponibilidad(request.getFuncionId());

        if (!disponibilidad.isDisponible() || disponibilidad.getEntradasDisponibles() < request.getCantidad()) {
            log.warn("Compra rechazada (409 Conflict): Entradas insuficientes para función ID: {}", request.getFuncionId());
            throw new InsufficientTicketsException(
                    String.format("Conflicto de disponibilidad: Solicitadas %d, disponibles %d",
                            request.getCantidad(), disponibilidad.getEntradasDisponibles())
            );
        }

        // 4. Descontar stock en Movie Service
        movieServiceClient.descontarEntradas(request.getFuncionId(), request.getCantidad());

        // Todo lo que sigue ocurre YA CON EL STOCK DESCONTADO: si algo falla, hay que compensar.
        try {
            // 5. Generar código único de ticket
            String codigoTicket = "PLP-" + LocalDateTime.now().getYear() + "-" +
                    UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // 6. Crear y guardar Ticket (nace PENDIENTE; pasa a PAGADO tras el pago simulado)
            Ticket ticket = Ticket.builder()
                    .fechaCompra(LocalDateTime.now())
                    .codigo(codigoTicket)
                    .estado(EstadoTicket.PENDIENTE)
                    .usuario(usuario)
                    .funcionId(request.getFuncionId())
                    .cantidad(request.getCantidad())
                    .idempotencyKey(claveNormalizada)
                    .build();

            Ticket ticketGuardado = ticketRepository.save(ticket);

            // 7. Procesar pago simulado
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

            // Transicion explicita de estado tras el pago simulado
            ticketGuardado.setEstado(EstadoTicket.PAGADO);
            ticketRepository.save(ticketGuardado);

            log.info("Ticket emitido exitosamente. Código: {}, Pago ID: {}", codigoTicket, pagoGuardado.getId());

            // 8. Evento asincronico (AFTER_COMMIT): notifica al comprador que su ticket fue pagado.
            notificacionEventPublisher.publicar(EventoNotificacion.builder()
                    .tipo("TICKET_PAGADO")
                    .usuarioId(usuario.getId())
                    .tituloPelicula(disponibilidad.getPeliculaTitulo())
                    .codigoTicket(codigoTicket)
                    .cantidad(request.getCantidad())
                    .monto(montoTotal)
                    .sala(disponibilidad.getSala())
                    .build());

            return mapToDTO(ticketGuardado, disponibilidad.getPeliculaTitulo(), disponibilidad.getSala());

        } catch (RuntimeException e) {
            // Compensación: el cupo ya fue descontado en Movie Service; devolverlo al fallar la compra
            log.warn("Compra fallida tras descontar stock; compensando reposición de {} entradas en la función {}: {}",
                    request.getCantidad(), request.getFuncionId(), e.getMessage());
            try {
                movieServiceClient.reponerEntradas(request.getFuncionId(), request.getCantidad());
            } catch (RuntimeException ce) {
                log.error("FALLO DE COMPENSACIÓN: cupo descontado sin ticket para la función {}",
                        request.getFuncionId(), ce);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public TicketResponseDTO obtenerPorId(Long id) {
        Ticket t = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado con ID: " + id));
        DisponibilidadResponseDTO disp = movieServiceClient.consultarDisponibilidad(t.getFuncionId());
        return mapToDTO(t, disp.getPeliculaTitulo(), disp.getSala());
    }

    @Transactional(readOnly = true)
    public TicketResponseDTO obtenerPorCodigo(String codigo) {
        Ticket t = ticketRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado con código: " + codigo));
        DisponibilidadResponseDTO disp = movieServiceClient.consultarDisponibilidad(t.getFuncionId());
        return mapToDTO(t, disp.getPeliculaTitulo(), disp.getSala());
    }

    @Transactional
    public TicketResponseDTO devolverTicket(Long id) {
        Ticket t = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado con ID: " + id));

        if (t.getEstado() != EstadoTicket.PAGADO) {
            throw new TicketYaDevueltoException("El ticket " + t.getCodigo() + " no puede devolverse (estado: " + t.getEstado() + ").");
        }

        // Construir la respuesta ANTES de eliminar: la devolución elimina el ticket
        // de forma definitiva (y su pago por cascada) para que no aparezca en "Mis Entradas".
        DisponibilidadResponseDTO disp = movieServiceClient.consultarDisponibilidad(t.getFuncionId());

        // Ventana de devolución: solo hasta 1 hora antes del inicio de la función.
        // Los horarios se ingresan en hora local de Chile (wall-time), por eso se compara
        // con la hora actual de America/Santiago y no con la UTC del servidor.
        if (disp.getFecha() != null && disp.getHora() != null) {
            LocalDateTime inicioFuncion = LocalDateTime.of(disp.getFecha(), disp.getHora());
            LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Santiago"));
            if (ahora.isAfter(inicioFuncion.minusHours(1))) {
                throw new DevolucionNoPermitidaException(
                        "La devolución solo se permite hasta 1 hora antes del inicio de la función (inicio: "
                                + disp.getFecha() + " " + disp.getHora() + ").");
            }
        }

        TicketResponseDTO devueltoDTO = mapToDTO(t, disp.getPeliculaTitulo(), disp.getSala());

        // 1. Eliminar localmente PRIMERO (y forzar el DELETE dentro de la transaccion):
        //    si la reposición remota falla, el rollback restaura el ticket y no hay
        //    estado inconsistente (ticket borrado sin cupos liberados).
        ticketRepository.delete(t);
        ticketRepository.flush();

        // 2. Reponer los asientos en Movie Service (lock pesimista allá)
        movieServiceClient.reponerEntradas(t.getFuncionId(), t.getCantidad());

        // 3. Evento asincronico (AFTER_COMMIT): solo se publica si el commit confirma.
        notificacionEventPublisher.publicar(EventoNotificacion.builder()
                .tipo("TICKET_DEVUELTO")
                .usuarioId(t.getUsuario() != null ? t.getUsuario().getId() : null)
                .tituloPelicula(disp.getPeliculaTitulo())
                .codigoTicket(t.getCodigo())
                .cantidad(t.getCantidad())
                .sala(disp.getSala())
                .build());

        log.info("Ticket {} devuelto y eliminado definitivamente. Se liberaron {} entradas de la función {}.",
                t.getCodigo(), t.getCantidad(), t.getFuncionId());

        return devueltoDTO;
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> listarPorUsuario(Long usuarioId) {
        // Cache de disponibilidad por función: evita N+1 cuando hay varios tickets de la misma función
        Map<Long, DisponibilidadResponseDTO> cache = new HashMap<>();
        return ticketRepository.findByUsuarioIdOrderByFechaCompraDesc(usuarioId).stream()
                .map(t -> {
                    DisponibilidadResponseDTO disp = cache.get(t.getFuncionId());
                    if (disp == null) {
                        try {
                            disp = movieServiceClient.consultarDisponibilidad(t.getFuncionId());
                        } catch (Exception e) {
                            log.warn("No se pudo obtener disponibilidad para la función {}: {}",
                                    t.getFuncionId(), e.getMessage());
                        }
                        cache.put(t.getFuncionId(), disp);
                    }
                    String titulo = disp != null ? disp.getPeliculaTitulo() : null;
                    String sala = disp != null ? disp.getSala() : null;
                    return mapToDTO(t, titulo, sala);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> listarTodos() {
        return ticketRepository.findAll().stream()
                .map(t -> mapToDTO(t, null, null))
                .collect(Collectors.toList());
    }

    private String normalizarClave(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String limpia = idempotencyKey.trim();
        return limpia.length() > 64 ? limpia.substring(0, 64) : limpia;
    }

    private TicketResponseDTO mapToDTO(Ticket t, String peliculaTitulo, String sala) {
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
                .cantidad(t.getCantidad())
                .pago(pagoDto)
                .build();
    }
}
