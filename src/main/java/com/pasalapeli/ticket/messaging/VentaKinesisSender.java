package com.pasalapeli.ticket.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasalapeli.ticket.event.EventoVenta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

import java.util.UUID;

/**
 * Envia cada venta completada a Amazon Kinesis Data Streams (stream-ventas)
 * de forma asincrona (@Async) con el AWS SDK v2. El envio es best-effort:
 * una falla de Kinesis solo genera un log de error y nunca afecta la compra.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VentaKinesisSender {

    private final KinesisClient kinesisClient;
    private final ObjectMapper objectMapper;

    @Value("${app.kinesis.stream-ventas:stream-ventas}")
    private String streamName;

    @Async("kinesisExecutor")
    public void enviarAsync(EventoVenta evento) {
        try {
            String payload = objectMapper.writeValueAsString(evento);
            kinesisClient.putRecord(PutRecordRequest.builder()
                    .streamName(streamName)
                    // Partition key estable por venta: agrupa los registros de
                    // la misma venta en el mismo shard del stream.
                    .partitionKey(evento.getIdVenta() != null
                            ? String.valueOf(evento.getIdVenta())
                            : UUID.randomUUID().toString())
                    .data(SdkBytes.fromUtf8String(payload))
                    .build());
            log.info("Venta publicada en Kinesis: idVenta={}, monto={}, stream={}",
                    evento.getIdVenta(), evento.getMonto(), streamName);
        } catch (Exception ex) {
            // La metrica es best-effort: no debe tumbar la transaccion de negocio.
            log.error("No se pudo publicar la venta {} en Kinesis: {}",
                    evento.getIdVenta(), ex.getMessage());
        }
    }
}
