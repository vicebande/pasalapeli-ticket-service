package com.pasalapeli.ticket.controller;

import com.pasalapeli.ticket.dto.ComprarTicketRequestDTO;
import com.pasalapeli.ticket.dto.TicketResponseDTO;
import com.pasalapeli.ticket.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping("/comprar")
    public ResponseEntity<TicketResponseDTO> comprarTicket(
            @Valid @RequestBody ComprarTicketRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TicketResponseDTO respuesta = ticketService.procesarCompra(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> obtenerPorId(@PathVariable Long id, Authentication authentication) {
        TicketResponseDTO ticket = ticketService.obtenerPorId(id);
        // Defensa en profundidad: ademas del BFF, el dueño (o ADMIN) debe ser el autenticado
        ticketService.verificarAccesoAUsuario(authentication, ticket.getUsuarioId());
        return ResponseEntity.ok(ticket);
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<TicketResponseDTO> obtenerPorCodigo(@PathVariable String codigo, Authentication authentication) {
        TicketResponseDTO ticket = ticketService.obtenerPorCodigo(codigo);
        ticketService.verificarAccesoAUsuario(authentication, ticket.getUsuarioId());
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/devolver")
    public ResponseEntity<TicketResponseDTO> devolverTicket(@PathVariable Long id, Authentication authentication) {
        TicketResponseDTO ticket = ticketService.obtenerPorId(id);
        ticketService.verificarAccesoAUsuario(authentication, ticket.getUsuarioId());
        TicketResponseDTO respuesta = ticketService.devolverTicket(id);
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<TicketResponseDTO>> listarPorUsuario(@PathVariable Long usuarioId,
                                                                    Authentication authentication) {
        // IDOR: solo el dueño (correo == principal) o un ADMIN puede ver el historial
        ticketService.verificarAccesoAUsuario(authentication, usuarioId);
        return ResponseEntity.ok(ticketService.listarPorUsuario(usuarioId));
    }

    @GetMapping
    public ResponseEntity<List<TicketResponseDTO>> listarTodos() {
        // Restringido a ADMIN en SecurityConfig
        return ResponseEntity.ok(ticketService.listarTodos());
    }
}
