---
name: database-design
description: Apply this project's database conventions when creating, reviewing or modifying relational schemas, JPA entities, Flyway migrations, native SQL, constraints, indexes, auditing or soft deletes.
---

# Database Design & SQL Conventions Skill

Este documento define las reglas obligatorias y convenciones de diseño que debes aplicar al crear o modificar objetos en la base de datos relacional (Tablas, Índices, Vistas, Funciones, Triggers y Constraints).

---

## 1. Reglas Generales de Nomenclatura

- **Estilo:** Usa `snake_case` en minúsculas para todos los objetos de la base de datos.
- **Idioma:** Todos los nombres de objetos, columnas y comentarios deben ser en `inglés`.
- **Palabras reservadas:** Escribe las palabras clave SQL en Mayúsculas (`SELECT`, `CREATE TABLE`, `WHERE`).

---

## 2. Convenciones para Tablas y Columnas

### Tablas
- **Sustantivos en plural:** Usa nombres en plural para las tablas (ej. `users`, `order_items`, `invoices`).
- **Clave Primaria (PK):**
  - Toda tabla debe tener una PK llamada `id`.
  - Usa `BIGINT` autonumérico (o `UUID` si se especifica que el sistema es distribuido).

### Columnas
- **Claves Foráneas (FK):**
  - Formato: `<nombre_tabla_singular>_id` (ej. `user_id`, `product_id`).
  - Siempre debe declararse explícitamente la restricción `FOREIGN KEY ... REFERENCES`.
- **Campos de Auditoría Obligatorios:** Toda tabla de entidad principal debe incluir:
  - `created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL`
  - `updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL`
- **Soft Deletes:** Si se requiere borrado lógico, utiliza:
  - `deleted_at TIMESTAMP WITH TIME ZONE NULL`
- **Valores Booleanos:**
  - Prefijo con verbo de estado: `is_active`, `has_discount`, `can_login`.
  - Tipo: `BOOLEAN` (nunca `INT` o `VARCHAR` para representar estados binarios).

---

## 3. Constraints y Restricciones

- **Nomenclatura explícita de Constraints:**
  - Clave Primaria: `pk_<tabla>` (ej. `pk_users`)
  - Clave Foránea: `fk_<tabla_origen>_<tabla_destino>` (ej. `fk_orders_users`)
  - Restricción Unique: `uq_<tabla>_<columna>` (ej. `uq_users_email`)
  - Restricción Check: `chk_<tabla>_<condicion>` (ej. `chk_products_price_positive`)
- **Nullability:** Declara explícitamente `NOT NULL` en todas las columnas donde no se permitan valores nulos.

---

## 4. Índices

- **Nomenclatura:** `idx_<tabla>_<columna1>_<columna2>` (ej. `idx_orders_user_id_created_at`).
- **Reglas de creación:**
  - Indexa automáticamente todas las columnas que sean `FOREIGN KEY`.

---

## 5. Vistas (Views) y Vistas Materializadas

- **Nomenclatura:**
  - Vistas estándar: Prefijo `vw_` (ej. `vw_active_subscriptions`).
  - Vistas materializadas: Prefijo `mv_` (ej. `mv_monthly_sales_summary`).
- **Regla de oro:** No uses `SELECT *` dentro de una vista. Lista explícitamente todas las columnas.

---

## 6. Funciones y Procedimientos Almacenados

- **Nomenclatura:**
  - Funciones: Prefijo `fn_` en formato verbo + sustantivo (ej. `fn_calculate_order_total`).
  - Stored Procedures: Prefijo `sp_` (ej. `sp_archive_old_logs`).
- **Manejo de Transacciones:** Garantiza el uso de bloques `TRY...CATCH` / `BEGIN...EXCEPTION` y manejo explícito de `ROLLBACK`.

---

## 7. Triggers

- **Nomenclatura:** `trg_<tabla>_<accion>_<momento>`
  - Ejemplo: `trg_users_updated_at_before_update`

---

## 8. Anti-Patrones a Evitar (Don'ts)

- ❌ NO usen tipos de datos genéricos ambiguos (ej. `VARCHAR` sin longitud explícita o `TEXT` para campos cortos).
- ❌ NO uses nombres ambiguos como nombres de columna (ej. `user`, `date`, `status`, `type`, `name`).
- ❌ NO almacenes tipos de datos de moneda como `FLOAT` o `DOUBLE`; usa siempre `NUMERIC` o `DECIMAL`.
- ❌ NO crees tablas sin claves primarias.

## Aplicación a BankApplication

- Aplica estas reglas a todos los módulos, incluidos SQL nativo y pruebas. Actualiza conjuntamente SQL, mapeos JPA, consultas, pruebas y documentación. Conserva el contrato JSON salvo petición de cambio.
- La base se ha confirmado vacía: el esquema inicial se mantiene en V1 de cada módulo. Si en otro momento hay datos o una migración aplicada, comprueba ese estado antes de editar el historial de Flyway.
- Mantén `Client extends Person` con herencia `JOINED`: `clients.id` es PK y FK de `persons.id`. Esta decisión explícita prevalece sobre el nombre genérico de FK y sobre generar un segundo identificador.
- `client_projections.id` conserva el identificador del cliente recibido por Kafka; no genera otro. Los IDs de eventos siguen siendo UUID, apropiados para este sistema distribuido.
- En varias FK autorreferenciadas distingue el rol: `reversed_transaction_id` y `replaced_transaction_id`; usa sufijos de rol en sus constraints para evitar nombres duplicados.
- Una PK, UNIQUE o índice compuesto cuyo primer campo sea la FK ya cubre su indexación. Un índice parcial no cubre por sí solo todas las filas de una FK.
- Auditoría: todas las tablas incluyen `created_at` y `updated_at`, mantenidos por PostgreSQL. En herencia JOINED cada tabla audita sus propias escrituras; los campos heredados JPA se almacenan en `persons`.
- `deleted_at` marca la baja lógica de clientes y cuentas. Todo registro con `is_active = FALSE` debe tener `deleted_at`; al pasar a inactivo se asigna la fecha si todavía no existe. Todo registro con `is_active = TRUE` debe tener `deleted_at = NULL`. Las transacciones usan compensaciones y no se borran lógicamente.
- Los triggers de auditoría propagan excepciones. PostgreSQL no permite ejecutar ROLLBACK dentro de una función de trigger: la transacción que la invoca realiza el rollback. No introduzcas SQL inválido intentando trasladar literalmente TRY/CATCH o ROLLBACK de otro motor.
- Conserva TEXT para el payload completo de eventos, que no es un campo corto. No uses nombres ambiguos `date`, `type` o `number` para nuevas columnas: usa `transaction_date`, `transaction_type`, `account_type` y `account_number`.
- `bank_corporate` es propietaria de `catalog_types`, `catalog_values` y `work_areas`. `catalog_types` se identifica mediante `id` y no contiene `catalog_code`. Dentro de esa base, `catalog_values.catalog_type_id` y `work_areas.area_type_id` son FK reales. El tipo de área debe pertenecer al catálogo con ID `4`.
- Las referencias corporativas de otras bases almacenan únicamente IDs externos: `persons.dni_type_id`, `accounts.account_type_id`, `accounts.work_area_id`, `transactions.transaction_type_id` y `transactions.work_area_id`. No declares FK entre bases ni agregues validación síncrona HTTP o Kafka para esas referencias a menos que se indique explícitamente. La validación de integridad referencial se realiza en el servicio y no en la base de datos.
- Los IDs iniciales de catálogos son contratos de datos: DNI `101`–`103`, cuentas `201`–`202`, movimientos `301`–`302` y tipos de área `401`–`402`. Los movimientos usan `301` para depósito y `302` para retiro, por lo que el signo debe validarse tanto en servicio como en SQL.
