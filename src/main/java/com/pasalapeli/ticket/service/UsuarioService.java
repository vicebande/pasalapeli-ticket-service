package com.pasalapeli.ticket.service;

import com.pasalapeli.ticket.dto.UsuarioDTO;
import com.pasalapeli.ticket.entity.RolUsuario;
import com.pasalapeli.ticket.entity.Usuario;
import com.pasalapeli.ticket.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    @Transactional
    public UsuarioDTO ensureUsuario(String correo, String nombre) {
        String email = correo != null ? correo.trim().toLowerCase() : "";
        return usuarioRepository.findByCorreo(email)
                .map(this::toDto)
                .orElseGet(() -> {
                    Usuario nuevo = Usuario.builder()
                            .nombre((nombre == null || nombre.isBlank()) ? email : nombre)
                            .correo(email)
                            .password("")
                            .rol(RolUsuario.CLIENTE)
                            .build();
                    Usuario guardado = usuarioRepository.save(nuevo);
                    log.info("Usuario registrado automáticamente desde el BFF: {} (id {})", email, guardado.getId());
                    return toDto(guardado);
                });
    }

    private UsuarioDTO toDto(Usuario usuario) {
        return UsuarioDTO.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol())
                .build();
    }
}