# BankApplication

Tres microservicios Spring Boot 4.1.1, Java 21 y Maven. `client` administra personas/clientes en el puerto **8001**; `account` administra cuentas, movimientos y reportes en el **8000**; `corporate` administra catálogos y áreas de trabajo en el **8002**. PostgreSQL mantiene bases separadas y Kafka propaga cambios de clientes. Puede ejecutarse localmente o con Docker Compose.

## Despliegue en AWS

El ambiente AWS se define con Terraform en [infra/terraform/README.md](infra/terraform/README.md). La arquitectura utiliza ECS Fargate, ECR, un ALB interno, RDS PostgreSQL, Secrets Manager y CloudWatch en `us-east-2`. Kafka y Kafdrop siguen en la computadora local; `account` y `client` se conectan mediante un túnel SSH inverso sobre Session Manager.

Los tres servicios exponen endpoints de salud para ECS y el ALB:

```text
/actuator/health/liveness
/actuator/health/readiness
```

Únicamente `health` está expuesto por Actuator y no muestra detalles. Kafka usa listeners `PLAINTEXT` separados para Docker, Windows y AWS; el tramo entre la instancia de acceso y la computadora viaja cifrado por SSH y Kafka no se publica en Internet.

El orden del primer despliegue es:

1. Crear el bucket de estado y los dos usuarios IAM de automatización mediante los scripts locales.
2. Aplicar Terraform localmente e inicializar las tres bases y sus usuarios mediante SSM.
3. Crear una access key para cada usuario y guardarlas en los secrets del environment `development` de GitHub.
4. Configurar Kafka local con los tres listeners y crear los tópicos locales.
5. Abrir el túnel inverso de Kafka y ejecutar `Deploy AWS`.
6. Abrir el túnel SSM al ALB interno para probar las APIs.

Esta cuenta autentica GitHub Actions mediante dos usuarios IAM con permisos separados porque una SCP impide crear proveedores de identidad. OIDC con credenciales temporales sigue siendo la opción recomendada cuando la organización lo permita. Las claves permanentes requieren rotación manual y nunca deben guardarse en el repositorio o en Terraform state.

No se despliegan Kafka, Kafdrop, tópicos ni bases de pruebas en AWS. El equipo local y el túnel deben permanecer activos mientras los servicios AWS necesiten mensajería.

## Estructura

```text
Microservices/BankApplication/
├── pom.xml                   # Construcción conjunta, sin librería de dominio compartida
├── mvnw / mvnw.cmd / .mvn/
├── client/
│   ├── pom.xml, HELP.md, mvnw, mvnw.cmd, .mvn/
│   └── src/
│       ├── main/java/com/backend/client/
│       │   ├── controller/ model/ repository/ service/
│       │   ├── dto/ exception/ config/ messaging/
│       │   └── ClientApplication.java
│       ├── main/resources/application.yml, db/migration/
│       └── test/java/com/backend/client/
├── account/                  # Misma organización; AccountApplication.java
├── corporate/                # Catálogos y áreas de trabajo; CorporateApplication.java
├── scripts/create-databases.sql
└── docs/API.http, ARQUITECTURA.md, VERIFICACION.md
```

## Despliegue con Docker Compose

El archivo `compose.yml` construye y ejecuta exclusivamente los tres microservicios. Cada imagen contiene Java Runtime y el JAR de un solo servicio. PostgreSQL se ejecuta en Windows y Kafka/Kafdrop se administran desde su propio proyecto Docker Compose.

Requisitos previos:

- PostgreSQL debe estar iniciado en el host, escuchar en el puerto 5432 y contener las bases creadas por `scripts/create-databases.sql`.
- Los contenedores externos `kafka` y `kafdrop` deben estar activos. Kafka debe pertenecer a la red `kafka_default`, anunciar `kafka:19092` y tener creados los tópicos descritos en la sección de preparación local.

Desde `BankApplication`:

```powershell
Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
```

Servicios publicados:

| Servicio | Dirección |
| --- | --- |
| Account | `http://localhost:8000` |
| Client | `http://localhost:8001` |
| Corporate | `http://localhost:8002` |
| Kafdrop externo | `http://localhost:9000` |
| Kafka externo para aplicaciones del host | `localhost:9092` |
| PostgreSQL instalado en Windows | `localhost:5432` |

Desde los contenedores, los tres servicios acceden a PostgreSQL mediante `host.docker.internal:5432`. `client` y `account` también se conectan a la red externa `kafka_default` y usan el listener interno `kafka:19092`. Esos valores pueden cambiarse en `.env`.

Consultar logs:

```powershell
docker compose logs -f client account corporate
```

Detener los tres microservicios:

```powershell
docker compose down
```

Este comando no detiene ni modifica PostgreSQL, Kafka o Kafdrop. Para cambiar puertos, credenciales o direcciones externas, editar `.env`; el archivo no se versiona.

## Preparación local

1. Instalar Java 21 y PostgreSQL 16 o superior. Maven Wrapper descarga Maven 3.9.16 en la primera ejecución; también puede usarse Maven 3.9.16 instalado.
2. Ejecutar `psql -U postgres -f scripts/create-databases.sql`. El script crea el rol local `bank`, las bases `bank_client`, `bank_account`, `bank_corporate` y sus tres bases de pruebas. Ejecutarlo una vez en una instalación preparada para este proyecto. Las credenciales de ejemplo son `bank` / `bank`.
3. Iniciar un broker Kafka local en `localhost:9092`, con listeners accesibles desde la máquina de Java. En una distribución Kafka 4.x KRaft de un solo nodo:

```bash
KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
bin/kafka-storage.sh format --standalone -t "$KAFKA_CLUSTER_ID" -c config/server.properties
bin/kafka-server-start.sh config/server.properties
```

El formateo se realiza solo sobre un directorio de datos nuevo. Para un broker existente, conservar su configuración y omitirlo. En Windows puede utilizarse una instalación Kafka en WSL con el puerto 9092 accesible desde Windows. No se instala ni modifica automáticamente ningún servidor externo.

Crear los tópicos desde la instalación Kafka:

```bash
bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic bank.clients.v1 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic bank.clients.v1.DLT --partitions 3 --replication-factor 1
bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic bank.clients.integration.v1 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic bank.clients.integration.v1.DLT --partitions 3 --replication-factor 1
```

Si se crean desde el contenedor Kafka externo:

```powershell
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --create --if-not-exists --topic bank.clients.v1 --partitions 3 --replication-factor 1
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --create --if-not-exists --topic bank.clients.v1.DLT --partitions 3 --replication-factor 1
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --create --if-not-exists --topic bank.clients.integration.v1 --partitions 3 --replication-factor 1
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --create --if-not-exists --topic bank.clients.integration.v1.DLT --partitions 3 --replication-factor 1
```

El tópico de errores debe tener al menos tantas particiones como el principal. La replicación 1 sirve para el broker local; no aporta alta disponibilidad.

## Compilar e iniciar

Desde `BankApplication`, en PowerShell:

```powershell
.\mvnw.cmd clean verify
```

En Linux/macOS: `sh mvnw clean verify`. Cada módulo también contiene su propio Wrapper y puede construirse por separado.

En tres terminales independientes:

```powershell
# Terminal de clientes
$env:DB_URL='jdbc:postgresql://localhost:5432/bank_client'
$env:DB_USER='bank'
$env:DB_PASSWORD='bank'
$env:KAFKA_BOOTSTRAP_SERVERS='localhost:9092'
java -jar client/target/client-1.0.0.jar
```

```powershell
# Terminal de cuentas
$env:DB_URL='jdbc:postgresql://localhost:5432/bank_account'
$env:DB_USER='bank'
$env:DB_PASSWORD='bank'
$env:KAFKA_BOOTSTRAP_SERVERS='localhost:9092'
java -jar account/target/account-1.0.0.jar
```

```powershell
# Terminal de datos corporativos
$env:DB_URL='jdbc:postgresql://localhost:5432/bank_corporate'
$env:DB_USER='bank'
$env:DB_PASSWORD='bank'
java -jar corporate/target/corporate-1.0.0.jar
```

Alternativa durante desarrollo: `mvn -f client/pom.xml spring-boot:run` y `mvn -f account/pom.xml spring-boot:run`. Las variables se configuran en cada terminal. Los valores predeterminados corresponden al entorno local anterior. Flyway crea las tablas al iniciar y JPA valida el esquema. `SERVER_ADDRESS` permite cambiar la dirección de escucha; por defecto se utiliza `127.0.0.1`.

## Person y Client: herencia con clave compartida

`Person` es una entidad JPA abstracta con `@Inheritance(strategy = JOINED)` y tabla `persons`. `Client` conserva `extends Person` y utiliza `@PrimaryKeyJoinColumn(name = "id")`. La columna `clients.id` es PK y FK a `persons.id`: relación uno a uno en la base de datos. JPA inserta primero la persona, genera su ID y lo reutiliza al insertar el cliente.

- `persons`: `id`, `first_name`, `last_name`, `complete_name`, `dni_type_id`, `dni`, `gender`, `age`, `address`, `phone`, `version`, `created_at`, `updated_at`.
- `clients`: `id`, `password`, `is_active`, `deleted_at`, `created_at`, `updated_at`.

La migración V1 crea directamente el esquema definitivo con los nombres separados, el nombre completo y la referencia externa al tipo de documento. Solo `persons.id` genera identificadores; `clients.id` reutiliza ese valor mediante su PK/FK.

## Convenciones del modelo de datos

La guía del proyecto se encuentra en [.agents/skills/database-design/SKILL.md](.agents/skills/database-design/SKILL.md), basada en el archivo proporcionado por el usuario. [AGENTS.md](AGENTS.md) establece su lectura para cualquier modificación del modelo o del SQL en los tres módulos.

Las migraciones usan nombres en inglés y snake_case, tablas plurales, constraints con nombre e índices para todas las FK. Las referencias desde `client` y `account` hacia datos corporativos almacenan IDs externos y no tienen FK entre bases.

| Atributo Java / JSON | Columna PostgreSQL |
| --- | --- |
| Account.number | accounts.account_number |
| Account.accountTypeId | accounts.account_type_id |
| Account.accountCreatedAt | accounts.account_created_at |
| Account.workAreaId | accounts.work_area_id |
| Account.clientId | accounts.client_projection_id |
| Transaction.date | transactions.transaction_date |
| Transaction.transactionTypeId | transactions.transaction_type_id |
| Transaction.workAreaId | transactions.work_area_id |
| Transaction.reversalOf | transactions.reversed_transaction_id |
| Transaction.replacementOf | transactions.replaced_transaction_id |
| Transaction.reversed | transactions.is_reversed |
| ClientProjection.active (interno) | client_projections.is_active |
| OutboxEvent.published (interno) | outbox_events.is_published |
| Person.dniTypeId | persons.dni_type_id |

`client_projections` conserva el ID de cliente recibido por Kafka. La PK de `clients` sigue siendo FK de `persons`; no genera un segundo ID. Los eventos usan UUID.

Todas las tablas incluyen `created_at` y `updated_at`, con `DEFAULT CURRENT_TIMESTAMP NOT NULL`. Un trigger de auditoría por tabla actualiza `updated_at` incluso con SQL directo; la creación no se modifica automáticamente. Hibernate recupera los campos generados por la base. En JOINED, `persons` y `clients` auditan sus propias filas; los campos heredados de JPA corresponden a `persons`.

DELETE de clientes y cuentas establece `deleted_at` y `is_active=false`. PUT o PATCH con `isActive=false` aplica la misma baja lógica. Repetir la desactivación conserva la fecha original; restaurar con `isActive=true` limpia `deleted_at`. Una constraint impide guardar combinaciones inconsistentes. Los movimientos continúan usando compensaciones, no borrado lógico.

## Contratos REST

| Recurso | Operaciones |
| --- | --- |
| `/api/clients` | GET paginado, POST |
| `/api/clients/{id}` | GET, PUT, PATCH, DELETE lógico (204) |
| `/api/accounts` | GET paginado, POST |
| `/api/accounts/{id}` | GET, PUT, PATCH, DELETE lógico (204) |
| `/api/transactions` | GET paginado, POST; filtro GET `accountId` opcional |
| `/api/transactions/{id}` | GET, PUT/PATCH con reemplazo, DELETE con reversión (200) |
| `/api/transactions/clients/{clientId}/report` | GET con `dateTransactionStart` y `dateTransactionEnd` |
| `/api/catalog-types`, `/api/catalog-values`, `/api/work-areas` | GET paginado, GET por ID, POST, PUT y DELETE |

PUT requiere todos los campos del DTO. PATCH acepta únicamente campos mutables conocidos y rechaza valores nulos. POST devuelve 201. Los listados aceptan `page`, `size` y `sort`; las bajas lógicas permanecen visibles con `isActive=false`. No se ofrecen endpoints independientes de Person.

- `Client`: `firstName`, `lastName`, `completeName`, `dniTypeId`, `dni`, `gender`, `age` (0–150), `address`, `phone`, `password` e `isActive`. DNI único. La contraseña nunca aparece en respuestas ni eventos y se almacena con BCrypt.
- `Account`: `number` único de hasta 20 caracteres, `accountTypeId`, `clientId`, `initialAmount`, `isActive` y `workAreaId`. `balance` y `accountCreatedAt` son asignados por el servicio.
- `Transaction`: `accountId`, `transactionTypeId`, `amount` y `workAreaId`. Los IDs sembrados son 301 para depósito y 302 para retiro. Fecha UTC asignada por el servidor; no se permite cambiar la cuenta.
- `corporate` siembra tipos de documento (101–103), cuenta (201–202), transacción (301–302), área (401–402) y cuatro áreas de trabajo (1–4). Los otros servicios guardan esos IDs sin consultar la base corporativa.
- Las operaciones de dinero requieren cuenta y cliente activos. Un cliente nuevo puede tardar en sincronizarse por Kafka; crear su cuenta antes de la sincronización devuelve 409 con `Cliente todavía no sincronizado`. Reintentar después de que el evento haya sido consumido.

Los errores usan `application/problem+json`: 400 para solicitudes inválidas, 404 para registros inexistentes y 409 para conflictos de negocio, duplicados o concurrencia. Ejemplo de saldo insuficiente:

```json
{"type":"about:blank","title":"Conflict","status":409,"detail":"Saldo no disponible"}
```

### Reversiones

El historial no se borra. DELETE crea una compensación con importe opuesto, referencia `reversalOf` y marca `reversed=true` en el original. PUT/PATCH agrega esa compensación y un reemplazo con `replacementOf`, en una sola transacción. La respuesta de edición contiene el nuevo identificador. Para futuras ediciones se utiliza ese nuevo identificador.

No se revierte dos veces el mismo movimiento ni se modifica una compensación. Tanto el saldo intermedio de la reversión como el final deben ser no negativos; si alguno falla, se deshace toda la operación. Así, no puede revertirse un depósito que ya fue gastado, aunque el reemplazo propuesto reponga ese dinero. DELETE repetido devuelve 409 y no genera dinero adicional. Las bajas de cuenta o cliente preservan toda la auditoría.

### Estado de cuenta

```text
GET /api/transactions/clients/1/report?dateTransactionStart=2026-09-01&dateTransactionEnd=2026-09-30
```

Ambos días se incluyen completos en UTC. La respuesta contiene `clientId`, `clientName`, fechas y `accounts[]`; cada cuenta incluye `currentBalance`, `openingBalance`, `closingBalance` y `transactions[]` ordenadas por fecha e identificador. Las cuentas sin movimientos y las inactivas se incluyen. Un período anterior a la creación tiene saldo inicial/final cero; el importe inicial entra en el saldo desde la creación de la cuenta. Si el importe inicial fue editado antes de cualquier movimiento, el reporte toma su valor vigente. Los movimientos originales y las compensaciones se muestran juntos, con sus vínculos de auditoría. Un cliente sin cuentas devuelve una lista vacía.

Ejemplos ejecutables en [docs/API.http](docs/API.http).

## Pruebas

`mvn test` ejecuta únicamente las pruebas unitarias; no requiere PostgreSQL ni Kafka.

Para las pruebas de integración, preparar las tres bases de pruebas y los tópicos anteriores. `client` y `account` se ejecutan con su perfil `integration`; `corporate` valida su modelo durante `mvn verify`.

```powershell
$env:TEST_DB_USER='bank'
$env:TEST_DB_PASSWORD='bank'
$env:TEST_KAFKA_BOOTSTRAP_SERVERS='localhost:9092'
$env:TEST_DB_URL='jdbc:postgresql://localhost:5432/bank_client_test'
.\mvnw.cmd -f client/pom.xml -Pintegration verify
$env:TEST_DB_URL='jdbc:postgresql://localhost:5432/bank_account_test'
.\mvnw.cmd -f account/pom.xml -Pintegration verify
```

Sin `TEST_DB_URL`, cada módulo utiliza automáticamente su propia base de pruebas. No establecer una URL compartida al probar ambos módulos desde la raíz. Las pruebas agregan registros con identificadores únicos y no limpian bases externas automáticamente. Los servidores de aplicación no deben consumir el tópico de integración: se reserva a las pruebas, que se ejecutan secuencialmente.

- `client/.../SampleTest.java`: prueba unitaria de herencia y estado de Client (F5).
- `account/.../SampleTest.java`: integración real contra PostgreSQL: saldos, compensaciones, rollback, concurrencia y reportes (F6).
- Pruebas adicionales de validación, cifrado, proyecciones, Kafka y contratos HTTP.

El resultado de la verificación realizada al generar el proyecto está en [docs/VERIFICACION.md](docs/VERIFICACION.md). No confundir pruebas implementadas con pruebas ejecutadas.

## Trazabilidad

| Requisito | Implementación |
| --- | --- |
| F1 CRUD | Controllers y servicios de Client, Account y Transaction; bajas lógicas y reversiones documentadas |
| F2 movimientos y saldo | TransactionService, BigDecimal, transacciones de base de datos y bloqueo de cuenta |
| F3 saldo insuficiente | BusinessException y GlobalExceptionHandler, HTTP 409, mensaje exigido |
| F4 reporte JSON | Endpoint exacto, rango UTC y saldos por cuenta |
| F5 prueba de Client | `client/src/test/java/.../SampleTest.java` |
| F6 integración | `account/src/test/java/.../SampleTest.java`, perfil `integration` |

[Arquitectura y decisiones operativas](docs/ARQUITECTURA.md). Se asume una moneda; no se incluye frontend ni autenticación.
