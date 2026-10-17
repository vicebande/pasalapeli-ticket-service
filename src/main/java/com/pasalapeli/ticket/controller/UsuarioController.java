package com.pasalapeli.ticket.controller;

import com.pasalapeli.ticket.dto.EnsureUsuarioRequestDTO;
import com.pasalapeli.ticket.dto.UsuarioDTO;
import com.pasalapeli.ticket.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping("/ensure")
    public ResponseEntity<UsuarioDTO> ensure(@Valid @RequestBody EnsureUsuarioRequestDTO request) {
        return ResponseEntity.ok(usuarioService.ensureUsuario(request.getCorreo(), request.getNombre()));
    }
}