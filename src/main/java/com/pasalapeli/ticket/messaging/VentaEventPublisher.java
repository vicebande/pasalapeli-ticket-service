package com.pasalapeli.ticket.messaging;

import com.pasalapeli.ticket.event.EventoVenta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica eventos de metricas de venta hacia Amazon Kinesis (stream-ventas)
 * de forma desacoplada de la logica de negocio, con el mismo patron que
 * {@link NotificacionEventPublisher}: el envio real ocurre AFTER_COMMIT para
 * no metricar transacciones que finalmente revierten, y los errores del
 * broker no rompen la operacion (best-effort).
 *
 * El envio se delega en {@link VentaKinesisSender} (@Async) para que el hilo
 * de la transaccion jamas espere a AWS.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VentaEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final VentaKinesisSender ventaKinesisSender;

    /** Encola el evento para publicarlo cuando la transaccion confirme. */
    public void publicar(EventoVenta evento) {
        applicationEventPublisher.publishEvent(evento);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void enviar(EventoVenta evento) {
        ventaKinesisSender.enviarAsync(evento);
    }
}
