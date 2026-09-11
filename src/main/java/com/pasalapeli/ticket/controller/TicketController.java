package com.pasalapeli.ticket.controller;

import com.pasalapeli.ticket.dto.ComprarTicketRequestDTO;
import com.pasalapeli.ticket.dto.TicketResponseDTO;
import com.pasalapeli.ticket.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping("/comprar")
    public ResponseEntity<TicketResponseDTO> comprarTicket(@Valid @RequestBody ComprarTicketRequestDTO request) {
        TicketResponseDTO respuesta = ticketService.procesarCompra(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.obtenerPorId(id));
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<TicketResponseDTO> obtenerPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(ticketService.obtenerPorCodigo(codigo));
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<TicketResponseDTO>> listarPorUsuario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(ticketService.listarPorUsuario(usuarioId));
    }

    @GetMapping
    public ResponseEntity<List<TicketResponseDTO>> listarTodos() {
        return ResponseEntity.ok(ticketService.listarTodos());
    }
}
