# Microservicio account

Java 21, Spring Boot 4.1.1 y Maven Wrapper 3.9.16.

Desde esta carpeta: `./mvnw test` (Linux/macOS) o `.\mvnw.cmd test` (Windows). Para generar el ejecutable: `./mvnw verify`. Iniciar con `java -jar target/account-1.0.0.jar`.

La base predeterminada es `bank_account`; PostgreSQL y Kafka deben estar disponibles. Configurar `DB_URL`, `DB_USER`, `DB_PASSWORD` y `KAFKA_BOOTSTRAP_SERVERS` cuando difieran del entorno local.

Consultar [README general](../README.md) para preparación, contratos y pruebas de integración (`-Pintegration`). Las pruebas externas usan `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` y `TEST_KAFKA_BOOTSTRAP_SERVERS`.
