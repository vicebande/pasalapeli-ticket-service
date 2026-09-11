package com.pasalapeli.ticket.repository;

import com.pasalapeli.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByCodigo(String codigo);
    List<Ticket> findByUsuarioIdOrderByFechaCompraDesc(Long usuarioId);
    List<Ticket> findByFuncionId(Long funcionId);
}
