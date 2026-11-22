package com.pasalapeli.ticket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;

/**
 * Cliente Kinesis del SDK v2 con la cadena de credenciales por defecto
 * (variables de entorno -> sistema -> perfil de instancia EC2 / LabRole).
 */
@Configuration
public class KinesisConfig {

    @Bean
    public KinesisClient kinesisClient(@Value("${AWS_REGION:us-east-1}") String region) {
        return KinesisClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
