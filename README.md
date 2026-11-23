# Transporter Assignment on Lanes

A Spring Boot service that assigns transporters to freight lanes across India at the **lowest total cost**, using
**at most** a user-specified number of transporters, while **every lane is covered**.

## Contents

1. [Problem statement](#problem-statement)
2. [Technology stack](#technology-stack)
3. [Architecture](#architecture)
4. [Database](#database)
5. [Optimization approach](#optimization-approach)
6. [Prerequisites](#prerequisites)
7. [Configuration](#configuration)
8. [Running the application](#running-the-application)
9. [Running the tests](#running-the-tests)
10. [API reference](#api-reference)
11. [Errors and HTTP status codes](#errors-and-http-status-codes)
12. [Verification status](#verification-status)
13. [Project layout](#project-layout)
14. [Assumptions and limitations](#assumptions-and-limitations)

## Problem statement

Each transporter quotes a price for some or all lanes. Given a limit **K** (`maxTransporters`), choose a set of
transporters and assign each lane to one of them so that:

1. **Cost minimization:** the sum of the assigned quotes is as low as possible.
2. **Transporter usage:** at most K distinct transporters are used. K is an upper limit, not a target: more
   transporters are used only when that lowers the cost.
3. **Full lane coverage:** every lane is assigned to exactly one transporter that quotes it.

The number of lanes and transporters is dynamic. The service exposes two endpoints: one to submit the data and one
to compute the optimal assignment.

## Technology stack

| Concern       | Choice                                                         |
|---------------|----------------------------------------------------------------|
| Language      | Java 21 (LTS)                                                  |
| Framework     | Spring Boot 3.5.16 (Web, Validation)                           |
| Persistence   | Spring Data JPA (Hibernate ORM 6.6)                            |
| Database      | PostgreSQL 17 (application), H2 in PostgreSQL mode (tests only) |
| Migrations    | Flyway                                                         |
| API docs      | springdoc-openapi 2.8.17 (OpenAPI 3.1, Swagger UI)             |
| Build         | Maven, via the included wrapper `./mvnw` (Maven 3.9.16)        |
| Testing       | JUnit 5, Mockito, AssertJ, MockMvc, Spring Boot Test           |

## Architecture

```
HTTP ──► controller ──► service ──────────────► optimizer (plain Java)
          │  DTOs        │  validation,             AssignmentSolver
          │  Bean        │  transactions            └─ ExactCombinationSolver
          │  Validation  ▼
          │            repository ──► JPA entities ──► PostgreSQL (Flyway schema)
          ▼
      exception (GlobalExceptionHandler: one error format for every failure)
```

| Package      | Responsibility                                                                                     |
|--------------|----------------------------------------------------------------------------------------------------|
| `controller` | `TransporterController`: binds and validates requests and delegates to services. No business logic. |
| `dto`        | Request/response records. JPA entities are never exposed through the API.                          |
| `service`    | `TransporterInputService` (transactional dataset replacement), `InputDataValidator` (rules that span the whole request), `AssignmentService` (loads data, runs the optimizer, maps the result). |
| `optimizer`  | The optimization engine. **No Spring or JPA dependency**; a test enforces this.                    |
| `domain`     | JPA entities `Lane`, `Transporter`, `LaneQuote`.                                                   |
| `repository` | Spring Data JPA repositories.                                                                      |
| `exception`  | Domain exceptions and the global exception handler.                                                |
| `config`     | Solver bean (with configurable limits) and OpenAPI metadata.                                       |

The optimizer is plain Java behind the `AssignmentSolver` interface. It takes an `OptimizationProblem` (lane ids,
transporter ids, quotes, K) and returns an `AssignmentSolution`, so it can be tested and replaced without touching
the web or persistence layers.

## Database

**PostgreSQL is the application database.** The schema is created by the Flyway migration
`src/main/resources/db/migration/V1__create_schema.sql`. Hibernate only *validates* it (`ddl-auto: validate`) and
never changes it.

**H2 is used only for tests.** It is a test-scoped Maven dependency, so it is not on the application's runtime
classpath, and it is configured only in `src/test/resources/application-test.yml`, which tests activate with
`@ActiveProfiles("test")`. Tests run the same Flyway migration on H2 in PostgreSQL compatibility mode, so they need
no running database.

### Schema

```
lane (id PK)  ◄──  lane_quote (lane_id FK, transporter_id FK)  ──►  transporter (id PK)
```

| Table         | Column           | Type                      | Constraints                                   |
|---------------|------------------|---------------------------|-----------------------------------------------|
| `lane`        | `id`             | BIGINT                    | primary key, `> 0`, supplied by the client    |
|               | `origin`         | VARCHAR(100)              | not null                                      |
|               | `destination`    | VARCHAR(100)              | not null                                      |
| `transporter` | `id`             | BIGINT                    | primary key, `> 0`, supplied by the client    |
|               | `name`           | VARCHAR(200)              | not null                                      |
| `lane_quote`  | `id`             | BIGINT identity           | primary key                                   |
|               | `lane_id`        | BIGINT                    | not null, FK → `lane(id)`                     |
|               | `transporter_id` | BIGINT                    | not null, FK → `transporter(id)` (indexed)    |
|               | `quote`          | BIGINT                    | not null, `1 ≤ quote ≤ 1,000,000,000,000`     |
|               |                  |                           | `UNIQUE (lane_id, transporter_id)`            |

- **Entities:** `LaneQuote` has `@ManyToOne` links to `Lane` and `Transporter`. The parent entities have no
  collections, so loading a dataset takes three simple queries and nothing cascades.
- **Replacement:** submitting input replaces the whole dataset in **one transaction**. Quotes, then transporters,
  then lanes are deleted, and the new rows are inserted. Any failure rolls everything back, so the previous dataset
  is preserved.
- **Money:** quotes are whole rupees stored as integers. No floating-point type is used for money.

## Optimization approach

### Rules

For K = `maxTransporters`. K is an **upper limit, not an exact count**, and a value above the number of transporters
is capped to that number:

1. Consider every combination of 1 to K transporters (an exact search, not a heuristic).
2. Within a combination, each lane goes to the member with the lowest quote for it. Missing quotes are ignored.
3. A combination that leaves any lane without a quote is rejected.
4. The total cost is the sum of the assigned quotes.
5. The answer is the valid combination with the lowest total cost.

### Tie-breaking

| Situation                                                | Winner                                                   |
|----------------------------------------------------------|----------------------------------------------------------|
| Two combinations have the same total cost                 | The one with **fewer transporters**                      |
| Same total cost and same number of transporters          | The **lexicographically smaller** ascending id list (e.g. `[1, 3]` before `[2, 3]`; ids compare as numbers) |
| Two transporters in the combination quote a lane equally | The **lower transporter id**                             |

These rules guarantee that every selected transporter serves at least one lane. If one served none, dropping it
would give the same cost with fewer transporters, and that smaller combination would have won.

### Algorithm (exact, not heuristic)

`ExactCombinationSolver` enumerates the combinations of size 1 to K depth-first, in ascending id order:
`{1}, {1,2}, {1,2,3}, …, {1,3}, …, {2}, …`. Each combination is its parent plus one transporter, so the per-lane
best quotes are updated incrementally rather than recomputed:

```
best[lane] of (parent + t) = min(best[lane] of parent, quote(lane, t))
```

The running total and the number of uncovered lanes are updated in the same pass. Because combinations of the same
size are visited in lexicographic order, the tie-break rules need no extra sorting:

- A combination replaces the current best only if it is strictly cheaper, or equally cheap with fewer members.
- On a lane, a new quote replaces the current one only if it is strictly lower.

Correctness follows because every allowed combination is visited exactly once. For a fixed combination, lanes are
independent (there are no capacity limits), so picking the cheapest quote per lane is optimal for that combination,
and the tie-break order selects exactly one winner. A brute-force reference solver (test scope only) cross-checks
the result on 1,500 random problems.

**Example.** Three lanes, three transporters (`–` means no quote), K = 2:

|        | T1   | T2   | T3   |
|--------|------|------|------|
| Lane 1 | 4000 | 6000 | –    |
| Lane 2 | 9000 | 3000 | 5000 |
| Lane 3 | –    | 8000 | 2000 |

- {T1} and {T3} are rejected because they leave a lane uncovered.
- {T2} costs 17,000 and {T1, T2} costs 15,000.
- {T1, T3} and {T2, T3} both cost **11,000** with two transporters each. The tie goes to `[1, 3]`.

The result is 11,000 with lane 1 → T1 and lanes 2 and 3 → T3. This is the example used in Swagger UI and below.

### Complexity and safeguards

Let n be the number of transporters, L the number of lanes, and C = C(n,1) + … + C(n,K) the number of combinations.

- **Time:** O(L · C). For a fixed K this grows polynomially in n (about n^K / K!). Only K = n reaches 2ⁿ − 1.
- **Space:** O(L · n) for the price table plus O(L · K) for the search.

Both limits below are checked from the input sizes alone, before any large allocation or search. They are set in
`application.yml` and can be overridden with environment variables (see [Configuration](#configuration)):

| Safeguard     | Bounds                                 | Default       | When exceeded |
|---------------|----------------------------------------|---------------|---------------|
| Work limit    | L × C lane evaluations (running time)  | 100,000,000   | HTTP 422      |
| Matrix limit  | L × n price-table cells (memory)       | 10,000,000    | HTTP 422      |

For scale, about 83 million lane evaluations took about 0.2 s on a development laptop.

**Overflow.** Totals use `long`. A combination whose total would overflow is skipped (it cannot be the cheapest),
and only if every valid combination overflows does the request fail with 422. With quotes capped at
10<sup>12</sup>, this is unreachable in practice through the API.

## Prerequisites

- **JDK 21.** If several JDKs are installed (on macOS, list them with `/usr/libexec/java_home -V`), select 21 for
  the current shell:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  ```
- **PostgreSQL 17**, needed only to *run* the application, not to run the automated tests. For example, on macOS
  with Homebrew:
  ```bash
  brew install postgresql@17
  # postgresql@17 is versioned, so Homebrew does not put its commands (psql, createdb, ...) on PATH:
  echo 'export PATH="$(brew --prefix postgresql@17)/bin:$PATH"' >> ~/.zshrc
  source ~/.zshrc
  brew services start postgresql@17      # runs in the background and starts at login
  pg_isready                             # expect: "accepting connections"
  ```
- **Maven** does not need to be installed: `./mvnw` downloads the pinned version.

### One-time database setup

```bash
# A login role that is not a superuser; --pwprompt asks for its password interactively.
createuser --pwprompt transporter_lanes
# The application database, owned by that role.
createdb --owner=transporter_lanes transporter_lanes
```

Flyway creates the tables on the first start (see [Database migrations](#database-migrations)).

> **Local authentication note.** Homebrew's PostgreSQL trusts local connections by default (`trust` in
> `pg_hba.conf`), so locally the password is accepted but not checked. Any real deployment should use password
> authentication (for example `scram-sha-256`) and supply the password through `DB_PASSWORD`.

## Configuration

The database connection is read from environment variables. The defaults exist only for local development and
match the setup above; any other environment must set all three:

| Environment variable | Default (local development only)                     |
|----------------------|------------------------------------------------------|
| `DB_URL`             | `jdbc:postgresql://localhost:5432/transporter_lanes` |
| `DB_USERNAME`        | `transporter_lanes`                                  |
| `DB_PASSWORD`        | *(empty)*                                            |

No password is stored in the repository. Set it in your shell (or your deployment platform's secret store), never in
`application.yml`:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/transporter_lanes
export DB_USERNAME=transporter_lanes
read -rs DB_PASSWORD && export DB_PASSWORD   # type the password; it is not echoed or saved in shell history
```

The optimizer limits are Spring properties in `application.yml`. Like any Spring Boot property, they can be
overridden with environment variables:

| Property                                         | Environment variable                            | Default     |
|--------------------------------------------------|-------------------------------------------------|-------------|
| `transporter-lanes.optimizer.max-lane-evaluations` | `TRANSPORTER_LANES_OPTIMIZER_MAX_LANE_EVALUATIONS` | 100000000 |
| `transporter-lanes.optimizer.max-matrix-cells`     | `TRANSPORTER_LANES_OPTIMIZER_MAX_MATRIX_CELLS`     | 10000000  |

For example, to make the solver stricter for one run:

```bash
TRANSPORTER_LANES_OPTIMIZER_MAX_LANE_EVALUATIONS=10000000 ./mvnw spring-boot:run
```

## Running the application

With JDK 21 selected and the `DB_*` variables set (see [Configuration](#configuration)):

```bash
./mvnw spring-boot:run
```

The service listens on <http://localhost:8080>.

- **Swagger UI:** <http://localhost:8080/swagger-ui.html>, with descriptions, field constraints, request examples
  and every documented error response for both endpoints.
- **OpenAPI document:** <http://localhost:8080/v3/api-docs>.

### Database migrations

Migrations run **automatically at startup**; there is no separate migration command. On startup:

1. Flyway applies any pending scripts from `src/main/resources/db/migration` (currently only
   `V1__create_schema.sql`) and records them in the `flyway_schema_history` table.
2. Hibernate then validates the entities against the resulting schema (`ddl-auto: validate`). If they do not match,
   the application refuses to start.

The startup log shows `Successfully applied 1 migration to schema "public", now at version v1` on the first run
(later runs report the schema as up to date). To check from the database side:

```bash
psql -U transporter_lanes -d transporter_lanes \
     -c "SELECT version, description, success FROM flyway_schema_history;" \
     -c "\dt"
```

## Running the tests

```bash
./mvnw test      # 125 tests; uses in-memory H2, PostgreSQL not required
./mvnw verify    # full build
```

The automated tests use H2 in PostgreSQL mode, configured in `src/test/resources/application-test.yml`, so they need
no running database.

**Optional: run the database tests against real PostgreSQL.** Environment variables take precedence over
`application-test.yml`, so the integration, repository and documentation tests can be pointed at a PostgreSQL
database without editing any file. Use a **separate, disposable database**, because these tests delete all rows
before each test:

```bash
createdb --owner=transporter_lanes transporter_lanes_test
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/transporter_lanes_test
export SPRING_DATASOURCE_USERNAME=transporter_lanes
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
read -rs SPRING_DATASOURCE_PASSWORD && export SPRING_DATASOURCE_PASSWORD
./mvnw test -Dtest='TransporterApiIntegrationTest,LaneQuoteRepositoryTest,OpenApiDocumentationTest'
```

`TransporterLanesApplicationTests` is left out of that run because it deliberately asserts that the test profile
uses H2.

| Suite                                    | What it covers                                                                  |
|------------------------------------------|---------------------------------------------------------------------------------|
| Optimizer (plain JUnit)                  | Rules, tie-breaking, missing quotes, infeasibility, limits, overflow, concurrency; regression on the assignment CSV for K = 1–7; 1,500 randomized comparisons with a brute-force reference solver |
| `InputDataValidatorTest`                 | Each rule that spans the whole request; all errors reported together             |
| Service tests (Mockito)                  | No database writes on invalid input; deletes and inserts in foreign-key order; entity → problem → response mapping |
| `TransporterControllerTest` (`@WebMvcTest`) | Response bodies, nested validation paths, every status code                   |
| `LaneQuoteRepositoryTest` (`@DataJpaTest`) | The real migration's unique, check and foreign-key constraints                |
| `TransporterApiIntegrationTest`          | Full stack on the assignment dataset; replacement; rollback on failure during deletion and during insertion |
| `OpenApiDocumentationTest`               | Swagger UI is served over real HTTP; the OpenAPI document lists the right status codes; documented examples equal real responses |

The test run writes the generated OpenAPI document to `target/openapi.json`.

## API reference

Both endpoints accept and return `application/json`.

### 1. Submit input data: `POST /api/v1/transporters/input`

Replaces the stored dataset (all lanes, transporters and quotes) with the request's contents. The whole request is
validated first, and the replacement happens in one transaction. If anything fails, the previous dataset is kept.

| Field                                   | Type    | Rules                                                              |
|-----------------------------------------|---------|--------------------------------------------------------------------|
| `lanes`                                 | array   | required, at least one                                             |
| `lanes[].id`                            | integer | required, ≥ 1, unique                                              |
| `lanes[].origin`, `lanes[].destination` | string  | required, non-blank, ≤ 100 characters                              |
| `transporters`                          | array   | required, at least one                                             |
| `transporters[].id`                     | integer | required, ≥ 1, unique                                              |
| `transporters[].name`                   | string  | required, non-blank, ≤ 200 characters                              |
| `transporters[].laneQuotes`             | array   | required, may be empty                                             |
| `laneQuotes[].laneId`                   | integer | required, must be a lane in this request; at most one quote per lane per transporter |
| `laneQuotes[].quote`                    | integer | required, 1 to 1,000,000,000,000 rupees; decimals are rejected     |

Every lane must be quoted by at least one transporter.

**Request**

```bash
curl -X POST http://localhost:8080/api/v1/transporters/input \
  -H 'Content-Type: application/json' \
  -d '{
    "lanes": [
      {"id": 1, "origin": "Mumbai",  "destination": "Delhi"},
      {"id": 2, "origin": "Delhi",   "destination": "Kolkata"},
      {"id": 3, "origin": "Chennai", "destination": "Bengaluru"}
    ],
    "transporters": [
      {"id": 1, "name": "Transporter T1", "laneQuotes": [
        {"laneId": 1, "quote": 4000}, {"laneId": 2, "quote": 9000}]},
      {"id": 2, "name": "Transporter T2", "laneQuotes": [
        {"laneId": 1, "quote": 6000}, {"laneId": 2, "quote": 3000}, {"laneId": 3, "quote": 8000}]},
      {"id": 3, "name": "Transporter T3", "laneQuotes": [
        {"laneId": 2, "quote": 5000}, {"laneId": 3, "quote": 2000}]}
    ]
  }'
```

**Response: `200 OK`**

```json
{"status": "success", "message": "Input data saved successfully."}
```

The endpoint returns 200 rather than 201 because it replaces the dataset rather than creating a new addressable
resource.

### 2. Get optimized results: `POST /api/v1/transporters/assignment`

Computes the optimal assignment over the stored dataset.

| Field             | Type    | Rules                                                                               |
|-------------------|---------|-------------------------------------------------------------------------------------|
| `maxTransporters` | integer | required, ≥ 1; an upper limit. Values above the number of transporters are capped. |

**Request**

```bash
curl -X POST http://localhost:8080/api/v1/transporters/assignment \
  -H 'Content-Type: application/json' \
  -d '{"maxTransporters": 2}'
```

**Response: `200 OK`** (for the input above)

```json
{
  "status": "success",
  "totalCost": 11000,
  "assignments": [
    {"laneId": 1, "transporterId": 1},
    {"laneId": 2, "transporterId": 3},
    {"laneId": 3, "transporterId": 3}
  ],
  "selectedTransporters": [1, 3]
}
```

- `assignments` has exactly one entry per lane, ordered by lane id.
- `selectedTransporters` lists the distinct transporters used, in ascending order.
- `totalCost` is the sum of the assigned quotes.

### The assignment's sample dataset

The assignment's original CSV (9 lanes × 7 transporters, every transporter quoting every lane) is kept unchanged at
`src/test/resources/com/example/transporterlanes/optimizer/transporter_assignment_data.csv`. Rows are lanes
(`Lane 1` … `Lane 9` → lane ids 1–9), columns are transporters (`T1` … `T7` → transporter ids 1–7), and each cell is
that transporter's quote for that lane.

The same data is available as a ready-made request body at `src/test/resources/datasets/assignment-dataset.json`,
with identical ids and quotes. The CSV has no origin or destination, which the API requires, so the city names in
that file are illustrative only; they do not affect the optimization.

```bash
curl -X POST http://localhost:8080/api/v1/transporters/input \
  -H 'Content-Type: application/json' --data @src/test/resources/datasets/assignment-dataset.json
curl -X POST http://localhost:8080/api/v1/transporters/assignment \
  -H 'Content-Type: application/json' -d '{"maxTransporters": 3}'
```

```json
{
  "status": "success",
  "totalCost": 134876,
  "assignments": [
    {"laneId": 1, "transporterId": 4}, {"laneId": 2, "transporterId": 1}, {"laneId": 3, "transporterId": 4},
    {"laneId": 4, "transporterId": 4}, {"laneId": 5, "transporterId": 4}, {"laneId": 6, "transporterId": 1},
    {"laneId": 7, "transporterId": 1}, {"laneId": 8, "transporterId": 6}, {"laneId": 9, "transporterId": 6}
  ],
  "selectedTransporters": [1, 4, 6]
}
```

#### Expected CSV regression results

| maxTransporters (K) | totalCost | selectedTransporters | Transporter per lane 1–9 |
|---------------------|----------:|----------------------|--------------------------|
| 1                   | 201259    | [1]                  | 1 1 1 1 1 1 1 1 1        |
| 2                   | 148402    | [1, 4]               | 4 1 4 4 4 1 1 1 1        |
| 3                   | 134876    | [1, 4, 6]            | 4 1 4 4 4 1 1 6 6        |
| 4                   | 127844    | [1, 3, 4, 7]         | 4 1 4 4 3 1 1 3 7        |
| 5                   | 125045    | [1, 3, 4, 5, 7]      | 5 1 4 4 3 1 1 3 7        |
| 6                   | 125045    | [1, 3, 4, 5, 7]      | 5 1 4 4 3 1 1 3 7        |
| 7                   | 125045    | [1, 3, 4, 5, 7]      | 5 1 4 4 3 1 1 3 7        |

From K = 5 the cost stops falling. Because K is an upper limit, K = 6 and K = 7 still use five transporters: adding
a sixth or seventh cannot lower the cost. Values of K above 7 are capped to 7 and give the same result.

These expectations were computed independently of this code base (with a separate script), and they are asserted
by `CsvRegressionTest` (optimizer) and `TransporterApiIntegrationTest` (full API), including the per-lane
assignments.

## Errors and HTTP status codes

Every error uses the same body. `errors` lists field-level problems and is empty otherwise:

```json
{
  "status": "error",
  "message": "Request validation failed.",
  "errors": [
    {"field": "lanes[0].origin", "message": "must not be blank"},
    {"field": "transporters[0].laneQuotes[0].quote", "message": "must be greater than 0"}
  ]
}
```

| Status | Endpoint   | When                                                                 | `message` (example)                                                    |
|--------|------------|----------------------------------------------------------------------|------------------------------------------------------------------------|
| 400    | both       | A field violates its rules. `errors` names each field with its full path | `Request validation failed.`                                       |
| 400    | input      | The data is inconsistent: duplicate ids, unknown `laneId`, duplicate quote, unquoted lane. All problems are listed | `Input data is invalid.`           |
| 400    | both       | Malformed JSON or a wrong type, e.g. `5000.75` for a quote. The field is named when known | `Malformed request body.`              |
| 404    | assignment | No input data has been submitted yet                                 | `No input data found. Submit lanes and transporters to /api/v1/transporters/input first.` |
| 409    | input      | Conflicted with a concurrent dataset replacement; safe to retry      | `The dataset was modified by a concurrent request. Please retry.`      |
| 422    | assignment | No combination of at most K transporters covers every lane          | `No combination of at most 1 transporter(s) covers all 2 lanes.`       |
| 422    | assignment | The problem exceeds the work or matrix limit                         | `Search space too large: 104857500 lane evaluations needed (…), limit is 100000000. Reduce maxTransporters or the number of transporters.` |
| 422    | assignment | Every valid total exceeds the representable range                    | `Every feasible combination of at most 1 transporter(s) has a total cost above 9223372036854775807, which cannot be represented.` |
| 405, 415, … | both  | Wrong HTTP method, wrong content type, unknown path, and so on       | The standard reason phrase, e.g. `Method Not Allowed.`                 |
| 500    | both       | Unexpected error. Details are logged, never returned. On input, the previous dataset is kept | `An unexpected error occurred.`               |

Swagger UI shows a full example body for each of these.

## Verification status

Three different levels of verification apply to this project. They are listed separately so it is clear what each
one does and does not cover.

### 1. Automated tests (repeatable, run by `./mvnw test`)

**125 tests pass on Java 21** (0 failures, 0 skipped). They run against in-memory H2 in PostgreSQL mode, so anyone
can repeat them without a database. They cover the optimizer rules, the tie-breaking and safeguards, the CSV
regression for K = 1–7, 1,500 randomized comparisons with a brute-force solver, validation and every error status,
the database constraints of the real Flyway migration, transactional rollback during both deletion and insertion,
and the correctness of the Swagger examples. See [Running the tests](#running-the-tests).

### 2. Local verification against real PostgreSQL (performed once, manually)

The application was run against **PostgreSQL 17.12** (Homebrew, macOS on Apple Silicon) using only the `DB_*`
environment variables; no configuration file was changed. The following were checked:

- **Migrations and schema:** Flyway applied `V1__create_schema.sql` to an empty database, and Hibernate schema
  validation passed at startup. Every table, column type, length, constraint and index in PostgreSQL matched the
  migration.
- **Both APIs over real HTTP:**
  - `POST /input` and `POST /assignment` returned the documented responses.
  - The assignment's 9 × 7 dataset gave the expected results above for every K from 1 to 7 (and for K = 50).
  - Swagger UI was served at `/swagger-ui.html`.
- **Error handling:** every documented status was reproduced: each 400 case, 404, 405, 415, and both 422 cases
  (infeasible limit, problem too large).
- **Dataset replacement:** each submission fully replaced the previous dataset; nothing was merged or left behind.
- **Transactional rollback:** temporary database triggers forced PostgreSQL to fail partway through a replacement,
  once during deletion and once during insertion. In both cases the client received 500 and the previously stored
  dataset was unchanged. The triggers were removed afterwards.
- **Concurrent replacement:**
  - Two submissions were made to start at the same moment, three times.
  - Each time, one succeeded and the other received 409. The stored data was always one complete dataset, never a
    mix.
  - The conflict came from PostgreSQL's foreign-key check, which the API maps to 409.
- **Database tests on PostgreSQL:** the 29 integration, repository and documentation tests also passed against a
  separate PostgreSQL database (see the optional command in [Running the tests](#running-the-tests)).

This was a one-time local check on a development machine. It is not part of the automated suite and was not a load
or performance test.

### 3. Production deployment: not done

The application has **not** been deployed to any server or cloud environment and has **not** been tested in
production. Deployment concerns such as authentication, TLS, database password authentication and backups are
outside the scope of this assignment.

## Project layout

```
src/main/java/com/example/transporterlanes
├── controller   TransporterController, ApiExamples (Swagger examples)
├── dto          Request/response records with Bean Validation and OpenAPI annotations
├── service      TransporterInputService, InputDataValidator, AssignmentService
├── optimizer    AssignmentSolver, ExactCombinationSolver, OptimizationProblem, AssignmentSolution, ...
├── domain       Lane, Transporter, LaneQuote (JPA entities)
├── repository   Spring Data JPA repositories, QuoteRow read model
├── exception    GlobalExceptionHandler and domain exceptions
└── config       OptimizerConfig (solver bean), OpenApiConfig
src/main/resources
├── application.yml                    PostgreSQL, Jackson, optimizer limits, springdoc
└── db/migration/V1__create_schema.sql
src/test/resources
├── application-test.yml               H2 (PostgreSQL mode); tests only
├── datasets/assignment-dataset.json   the assignment's CSV as an API request
└── com/example/transporterlanes/optimizer/transporter_assignment_data.csv   the original assignment CSV
```

## Assumptions and limitations

- **One dataset at a time.** Each input submission replaces the previous one. There is no versioning or
  multi-tenancy.
- **Whole-rupee quotes.** Quotes are integers from 1 to 10<sup>12</sup>. Unknown JSON fields are ignored, which is
  Spring's default.
- **No capacity constraints.** A transporter can serve any number of lanes. Capacities would make lanes compete,
  and the problem would need min-cost flow or integer programming instead.
- **Exact search.** The optimizer always returns the true optimum. Very large K and n are refused (422) rather
  than approximated.
- **Concurrent submissions.** The assignment endpoint reads in a REPEATABLE READ transaction, so it never sees a
  half-replaced dataset. When two submissions overlap, one wins and the other gets 409 (safe to retry); the stored
  data is always one complete dataset. This was verified manually against PostgreSQL (see
  [Verification status](#verification-status)); the automated tests cover the 409 mapping but do not run concurrent
  transactions.
- **Database used in automated tests.** The automated tests run on H2 in PostgreSQL mode. Behaviour that depends on
  the real database (locking, isolation) was checked manually against PostgreSQL, not in the automated suite.
- **No authentication.** The API has no authentication or authorization; the assignment did not ask for it.
- **Not deployed.** See [Verification status](#verification-status).
