# AGENTS.md

This app is built on the Cozy framework: an internal business tool that runs on offline Windows PCs. The stack is Spring Boot (MVC), Thymeleaf with htmx and Alpine.js (CSP build), Tabler, MyBatis, SQLite, and Spring Modulith.

## Architecture

- Each package directly under the main class package is a module, and a module is one feature.
- A module uses another module only through that module's `spi`, and only after declaring it in `allowedDependencies` of its own `package-info.java`.
- `api/` holds controllers and screen DTOs (`api/dto`); screen templates live in `templates/{module}/api/`.
- `spi/` is the contract for other modules: `*Facade` for writes, `*Finder` for reads, `spi/dto` for values, and `templates/{module}/spi/` for public fragments.
- Events live in the event contracts module `events`, at `events/spi/{owner module}/`, and both the publisher and the listener declare only `"events :: spi"` for them, so modules that receive each other's events never form a dependency cycle.
- Only the owner module creates its events, in `internal/app/command`; event fields use basic types only, and `events` is never a feature module name.
- `internal/` holds the implementation: `app/command` (writes and listeners), `app/query` (reads), `domain`, `infra` (MyBatis mappers and rows), and `config`.
- Modules read each other through `*Finder` and write to each other only through events; a module never calls another module's `*Facade`.
- The SQLite schema lives in `db/migration` (Flyway), and SQL statements live in `mapper/{module}/*Mapper.xml`.
- Every screen is wrapped in a cozy-ui pattern and has a spec file `specs/{module}-{screen}.yml`.

## Screens: htmx and Alpine.js

- Use htmx for everything that goes through the server, such as search, paging, saving, validation errors, and delete.
- Use Alpine.js only for state that may be lost on reload, such as an open panel, a selection count, or copying text.
- Attach a component by name only (`x-data="cozyCopy"`), pass server values as `data-*` attributes, and write long attribute names (`x-on:click`).
- Use `cozy` components first, and add a module component with `cozy add behavior` in `static/{module}/{module}.js`.

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
