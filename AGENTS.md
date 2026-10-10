# Project instructions

Before creating, reviewing or changing the data model, JPA entities, Flyway migrations, native SQL, constraints, indexes, audit fields or soft deletes in any module, read and apply [.agents/skills/database-design/SKILL.md](.agents/skills/database-design/SKILL.md).

The skill contains the user's database conventions and the explicit project decisions that qualify those conventions. Keep entity mappings, SQL, affected tests and documentation consistent. Apply these instructions throughout this parent project and the `corporate`, `client` and `account` modules. References from `client` or `account` to corporate catalog values and work areas are external IDs; PostgreSQL foreign keys exist only inside `bank_corporate`.

PMD is mandatory for all three modules. Its Maven configuration is inherited from the parent `pom.xml` and checks production and test Java sources during the `verify` phase. Keep all PMD violations with priorities 1, 2 and 3 at zero; priority 1 is the most severe. Do not suppress a violation or exclude a source file unless `AGENTS.md` documents the concrete technical reason.

After changing Java code, run `mvn verify` from the parent project. Run `mvn -Pintegration verify` when the change affects integration behavior or infrastructure-backed tests. A change is not complete while PMD reports a priority 1, 2 or 3 violation.
