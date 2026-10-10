# Verificación de la entrega

## Reinicialización de bases y migraciones — 2026-10-09

- Se eliminaron y recrearon vacíos los esquemas `public` de `bank_client`, `bank_client_test`, `bank_account`, `bank_account_test`, `bank_corporate` y `bank_corporate_test`.
- Se eliminaron todos los datos, tablas, funciones, triggers e historiales `flyway_schema_history`. La comprobación posterior devolvió cero tablas en las seis bases.
- Cada módulo conserva únicamente `V1__schema.sql`; las migraciones anteriores de `client` y `account` se consolidaron en sus V1 definitivas.
- Las tres V1 se ejecutaron correctamente en esquemas temporales de PostgreSQL y la transacción de validación fue revertida antes de reinicializar las bases reales.

## Consistencia de baja lógica — 2026-10-09

- `client` y `account` asignan `deleted_at` al recibir `isActive=false` mediante POST, PUT, PATCH o DELETE; una desactivación repetida conserva la fecha y `isActive=true` la elimina.
- Las V1 definitivas de `client` y `account` incluyen una equivalencia estricta entre `is_active` y `deleted_at`.
- Ambas migraciones y sus constraints se ejecutaron en PostgreSQL dentro de transacciones con `ROLLBACK`; los datos y esquemas actuales no fueron modificados.
- La compilación limpia de 43 fuentes principales y 12 fuentes de prueba de ambos módulos terminó correctamente.
- PMD prioridades 1–3 terminó correctamente para los tres módulos. La ejecución de JUnit con Mockito quedó bloqueada durante la carga dinámica de Byte Buddy en este entorno y se interrumpió; no se informa como aprobada.

## Eliminación de catalog_code — 2026-10-09

- Se confirmó que `bank_corporate` y `bank_corporate_test` no tienen esquema ni historial de Flyway. Por ello, V1 crea `catalog_types` directamente sin `catalog_code`; no se añadió una V2 innecesaria.
- La entidad, los DTO y el contrato REST de tipos de catálogo contienen únicamente `id`, `name` y auditoría.
- La validación de áreas de trabajo usa el ID estable `4` del tipo de catálogo correspondiente.

## Datos corporativos y referencias externas — 2026-09-30

- Se añadió el módulo `corporate`, puerto 8002 y base `bank_corporate`, con CRUD para tipos de catálogo, valores y áreas de trabajo.
- La migración corporativa crea y siembra cuatro catálogos, nueve valores, una matriz y tres sucursales. `work_areas.area_type_id` referencia un valor perteneciente al tipo de catálogo con ID `4`.
- `client` guarda `dni_type_id`; `account` guarda `account_type_id`, `transaction_type_id` y `work_area_id`. Son IDs externos positivos, sin FK ni consulta síncrona entre bases.
- Las versiones anteriores de las migraciones de `client` y `account` fueron validadas en su momento; posteriormente se consolidaron en una única V1 por módulo durante la reinicialización del 2026-10-09.
- La migración V1 de `corporate` se ejecutó completa en un esquema temporal y se revirtió. Se validaron tablas, FK, triggers, semillas y secuencias.
- `mvn pmd:check`: correcto para `corporate`, `client`, `account` y el agregador, con prioridades 1–3 y fuentes de prueba incluidas.
- La compilación de fuentes principales fue correcta por módulo. El ciclo `mvn verify` continúa afectado en este entorno por `AccessDeniedException` de Java ZipFS al cerrar JAR de Spring durante `testCompile`; no se atribuye ese fallo al código fuente.
- Las bases `bank_corporate` y `bank_corporate_test` no se crearon porque el usuario `bank` no posee `CREATEDB` y no se dispone de credenciales administrativas. `scripts/create-databases.sql` quedó idempotente para que un administrador cree únicamente las bases faltantes.

## Convenciones de base de datos — 2026-09-22

- Skill del usuario guardada en `.agents/skills/database-design/SKILL.md`, conservando el documento original y añadiendo las decisiones específicas del proyecto. `AGENTS.md` exige consultarla para cambios del modelo o SQL en ambos módulos.
- Skill validada con `quick_validate.py` y PyYAML: correcta.
- Ambos V1 actualizados para la base vacía. Sintaxis SQL y PL/pgSQL comprobada con el parser PostgreSQL de pglast; se verificaron las siete tablas, auditoría, booleanos, constraints nombradas y cobertura completa de las FK mediante índices. Esta comprobación no ejecuta las migraciones en un servidor.
- Compilación directa con Java 21: 55 archivos Java correctos.
- JUnit: 17 pruebas locales ejecutadas, 17 aprobadas, 0 fallidas. Incluyen el modelo de mapeo construido por Hibernate y las reglas de baja/reactivación, además de los casos anteriores.
- Las pruebas de integración existentes se ampliaron para verificar timestamps, triggers y deleted_at. Siguen pendientes de ejecución contra PostgreSQL/Kafka externos; no se ha modificado ninguna base de datos.
- Se conserva la limitación histórica del ciclo Maven completo documentada abajo. La verificación actual se ejecutó con JavaCompiler y JUnit directamente.


## Refactorización de paquetes — 2026-09-22

- Paquetes y directorios principales y de pruebas: `com.backend.client` y `com.backend.account`. Imports, referencias completas, documentación y groupId Maven actualizados a `com.backend`.
- Compilación directa con Java 21: 51 archivos Java correctos, incluyendo pruebas de integración.
- JUnit: 13 pruebas unitarias ejecutadas, 13 aprobadas, 0 fallidas.
- `mvn validate`: correcto para los dos módulos y el agregador.
- Archivos compilados antiguos retirados del proyecto. ZIP actualizado sin rutas ni referencias a los paquetes anteriores.
- Las limitaciones de ejecución de integración y del ciclo Maven completo descritas más abajo siguen pendientes.

## Actualización Person / Client — 2026-09-22

- Person ahora es entidad con tabla persons; Client mantiene herencia JOINED y clave primaria compartida con FK hacia persons.
- Con la confirmación de que la base está vacía, V1 crea directamente persons y clients con PK/FK compartida. Se eliminó la segunda migración; version se define en persons. Se verificaron la estructura del SQL y la inclusión de una sola migración de client en el ZIP actualizado.
- Compilación directa del módulo client y sus pruebas: 20 archivos Java, correcta.
- Pruebas unitarias de client ejecutadas de nuevo: 6 correctas, 0 fallidas. Resultado en PRUEBAS-PERSON-CLIENT.txt.
- Integración ampliada para comprobar inserción en ambas tablas, actualización de Person, versión y FK contra clientes huérfanos. El proyecto contiene ahora 10 casos de integración.
- La migración y las pruebas PostgreSQL no se ejecutaron: localhost:5432 no estaba disponible.

## Verificación original — 2026-09-21

Fecha: 2026-09-21.

## Ejecutado correctamente

- Dependencias reales de Spring Boot 4.1.1 resueltas con Maven 3.9.16.
- `mvn validate`: SUCCESS para client, account y BankApplication.
- Compilación directa con JavaCompiler del JDK 21.0.11, `--release 21` y classpath resuelto por Maven: **51 archivos Java**, incluidos todos los servicios y las pruebas.
- JUnit Platform 6.0.3: **13 pruebas unitarias ejecutadas, 13 correctas, 0 fallidas**.
- Formato Java unificado con Google Java Format; POM XML y estructura verificados.

| Clase | Casos unitarios aprobados |
| --- | ---: |
| client.SampleTest | 1 |
| client.ClientServiceTest | 3 |
| client.OutboxPublisherTest | 2 |
| account.AccountServiceTest | 1 |
| account.TransactionServiceTest | 4 |
| account.ClientEventConsumerTest | 2 |

Cubren herencia y baja de Client, validación, BCrypt, exclusión de contraseña, PATCH, confirmación/fallo del publicador, acceso seguro a proxies JPA, depósitos/retiros, saldo insuficiente, cuentas inactivas, duplicados y eventos antiguos.

## Pendiente de infraestructura externa

Se implementaron y compilaron **9 casos de integración**, etiquetados `integration`, contra PostgreSQL real y Kafka real:

- ClientIntegrationTest: CRUD HTTP, contraseña y outbox transaccional.
- account.SampleTest: seis casos de persistencia de saldos, reversiones, reemplazos, rollback, retiros concurrentes, reportes y protección del importe inicial.
- AccountHttpIntegrationTest: contratos HTTP de cuentas, dinero, fechas y reportes.
- KafkaIntegrationTest: sincronización por Kafka, duplicados y baja de cliente.

No se ejecutaron: no se detectaron servidores escuchando en localhost:5432 y localhost:9092 y no se proporcionaron conexiones externas alternativas. El README contiene la preparación y los comandos `-Pintegration verify`.

## Limitación de Maven en este entorno

El ciclo completo `mvn clean verify` no quedó validado. Durante `mvn test`, Java produjo `java.nio.file.AccessDeniedException` al resolver rutas canónicas de dependencias dentro de Documents al cerrar ZipFS. `mvn clean test` también falló al limpiar target. Los permisos de lectura adicionales no resolvieron esa limitación del entorno.

Para comprobar el código se utilizó JavaCompiler en un proceso auxiliar, sin cerrar el administrador ZipFS afectado, y se ejecutó JUnit directamente. Esta comprobación aprobada **no equivale a afirmar que el ciclo Maven completo pasó**. El ZIP contiene las fuentes y configuraciones, excluye target y no incluye JAR precompilados. Ejecutar los comandos del README en el entorno local para generar los JAR y completar la integración.
