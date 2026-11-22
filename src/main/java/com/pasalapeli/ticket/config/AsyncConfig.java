package com.pasalapeli.ticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Habilita @Async con un pool dedicado para el envio de eventos de metricas
 * hacia Kinesis (nunca bloquea el hilo de la transaccion de negocio).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("kinesisExecutor")
    public Executor kinesisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("kinesis-");
        executor.initialize();
        return executor;
    }
}
