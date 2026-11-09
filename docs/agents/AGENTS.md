# AGENTS.md

This app is built on the Cozy framework: an internal business tool that runs on offline Windows PCs. The stack is Spring Boot (MVC), Thymeleaf with htmx, Tabler, MyBatis, SQLite, and Spring Modulith.

## Architecture

- Each package directly under the main class package is a module, and a module is one feature.
- A module uses another module only through that module's `spi`, and only after declaring it in `allowedDependencies` of its own `package-info.java`.
- `api/` holds controllers and screen DTOs (`api/dto`); screen templates live in `templates/{module}/api/`.
- `spi/` is the contract for other modules: `*Facade` for writes, `*Finder` for reads, `spi/dto` for values, `spi/event` for events, and `templates/{module}/spi/` for public fragments.
- `internal/` holds the implementation: `app/command` (writes and listeners), `app/query` (reads), `domain`, `infra` (MyBatis mappers and rows), and `config`.
- Modules read each other through `*Finder` and write to each other only through events; a module never calls another module's `*Facade`.
- The SQLite schema lives in `db/migration` (Flyway), and SQL statements live in `mapper/{module}/*Mapper.xml`.
- Every screen is wrapped in a cozy-ui pattern and has a spec file `specs/{module}-{screen}.yml`.

## Rules

- Create structure only with `cozy` commands, and read the `cozy-helper` skill (`.agents/skills/cozy-helper/SKILL.md`) before adding a module, screen, CRUD set, event, listener, migration, DTO, or Finder/Facade method.
- Write only the slots that commands leave (`cozy:slot`) and the business logic inside generated methods.
- Never disable a check or skip a test to make the build pass.
- Never edit an applied migration or change the name or type of an existing event field.
- Never reference an external URL (CDN, `http://`) or write an inline script or style.

## Verify

- Run `cozy check` for a fast check in seconds, then `mvn -o verify` for the final verdict.
- When a violation ends with `고치는 명령: cozy …`, run that command exactly as printed.

Respond in Korean unless the user message is in English.
