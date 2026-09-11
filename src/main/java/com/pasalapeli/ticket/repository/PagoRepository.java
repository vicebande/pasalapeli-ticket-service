package com.pasalapeli.ticket.repository;

import com.pasalapeli.ticket.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByTicketId(Long ticketId);
}
