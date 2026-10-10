# Microservicio corporate

Java 21, Spring Boot 4.1.1 y Maven Wrapper 3.9.16.

Desde esta carpeta: `./mvnw test` (Linux/macOS) o `.\mvnw.cmd test` (Windows). Para generar el ejecutable: `./mvnw verify`. Iniciar con `java -jar target/corporate-1.0.0.jar`.

La base predeterminada es `bank_corporate`. Configurar `DB_URL`, `DB_USER` y `DB_PASSWORD` cuando difieran del entorno local. Flyway crea los catálogos y las cuatro áreas de trabajo iniciales.

Consultar [README general](../README.md) para preparación, contratos e identificadores corporativos sembrados.
