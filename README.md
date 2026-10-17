# 🎟️ Pasa La Peli - Ticket Service

Microservicio encargado de la orquestación de compras de tickets, pagos simulados y validación de cupos en tiempo real con `movie-service`.

---

## 🚀 Tecnologías
- **Java 21**
- **Spring Boot 3.3.3**
- **Spring Data JPA / Hibernate**
- **MySQL 8.0**
- **RestTemplate / HTTP Client**
- **Docker & Dockerfile**

---

## ⚙️ Configuración (`application.yml`)
- **Puerto:** `8083`
- **Base de Datos:** `jdbc:mysql://localhost:3306/pasalapeli_db` (Tablas: `Ticket`, `Pago`, `Usuario`)
- **Comunicación con Movie Service:**
  - `services.movie-service.url`: `http://localhost:8082` (o `http://movie-service:8082` en Docker)

---

## 🔄 Flujo de Compra y Manejo de Concurrencia
1. Recibe solicitud `POST /api/tickets/comprar` con `{ usuarioId, funcionId, cantidad, metodoPago }`.
2. Consulta disponibilidad en tiempo real en `movie-service` (`GET /api/funciones/{id}/disponibilidad`).
3. **Si no hay disponibilidad suficiente:** Retorna explícitamente código **HTTP 409 Conflict**.
4. **Si hay disponibilidad:** 
   - Solicita el descuento de entradas en `movie-service`.
   - Genera código único `PLP-YYYY-XXXXXXXX`.
   - Registra el ticket con estado `PAGADO`.
   - Registra la transacción en la tabla `Pago` con estado `APROBADO`.

---

## 📡 Endpoints Principales
- `POST /api/tickets/comprar`: Procesa compra de entradas.
- `GET /api/tickets/{id}`: Detalle de ticket por ID.
- `GET /api/tickets/codigo/{codigo}`: Busca ticket por su código digital único.
- `GET /api/tickets/usuario/{usuarioId}`: Historial de compras de un usuario.
- `GET /api/tickets`: Lista todos los tickets (Admin).

---

## 🛠️ Ejecución Local
```bash
mvn clean package -DskipTests
mvn spring-boot:run
```
O con Docker:
```bash
docker build -t ticket-service .
docker run -p 8083:8083 ticket-service
```

---

## ☁️ Despliegue CI/CD (GitHub Actions → EC2)

Este repo se despliega **solo a sí mismo** sobre una instancia **EC2 (Ubuntu 24.04)** que ya porta el stack completo. El orquestador vive en el repo [`pasalapeli-database`](https://github.com) (contiene el `docker-compose.yml` global en `/opt/pasalapeli/`).

### Workflow `.github/workflows/deploy.yml`
En cada `push` a `main`:
1. SSH al EC2 (acción `appleboy/ssh-action`).
2. `git pull` del código de `ticket-service` en `/opt/pasalapeli/pasalapeli-ticket-service`.
3. `docker compose up -d --build ticket-service`.
4. Espera el estado `healthy` del contenedor vía `/actuator/health`.

### GitHub Secrets requeridos en este repo
| Secret | Descripción |
|---|---|
| `EC2_HOST` | IP pública del EC2 |
| `EC2_USER` | Usuario SSH (usualmente `ubuntu`) |
| `EC2_SSH_KEY` | Clave privada SSH (.pem) |

### Variables de entorno en producción (definidas en el `.env` del orquestador)
- `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/pasalapeli_db?...`
- `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`
- `MOVIE_SERVICE_URL=http://movie-service:8082`

---

## 🔔 Mensajería asincrónica (productor)

`TicketService` publica eventos en el exchange *topic* `pasalapeli.events` (RabbitMQ) que consume el **[Notification Service](https://github.com)**:

- **`TICKET_PAGADO`**: al confirmar una compra (notifica al comprador).
- **`TICKET_DEVUELTO`**: al procesar una devolución (notifica al comprador).

- El envío ocurre **después de confirmar la transacción** (`@TransactionalEventListener(AFTER_COMMIT)`) y es **best-effort** (un fallo del broker no rompe la compra/devolución).
- Clases: `event/EventoNotificacion.java`, `messaging/NotificacionEventPublisher.java`, `config/RabbitProducerConfig.java`.
- Variables: `SPRING_RABBITMQ_HOST=rabbitmq`, `SPRING_RABBITMQ_PORT=5672`, `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`, `NOTIF_EXCHANGE` (default `pasalapeli.events`).
