# Plan: Phase 2 Repository Processing

**Status:** draft - awaiting user approval before hs-build

**Baseline:** Phase 1 upload/version/status flow exists. Current backend has no dedicated parser AST dependency, no Spring AI/OpenRouter embedding client, no pgvector schema/migration, and no durable indexing job queue.

## Scope Guard

- Source of truth: `requirements.md` sections Repository Indexing and RAG Requirements; `design.md` sections Indexing Pipeline, Chunking Strategy, `indexing` package, and `embedding` package.
- MVP language scope for Phase 2: Java, TypeScript, JavaScript, SQL, Markdown, YAML, JSON, XML. HTML/CSS may be extracted as text/logical blocks only if already encountered by the shared extractor, but they are not first-class AST targets in this phase.
- Chunking must be semantic-first: package/class/method/logical block. Fixed-token chunking is not allowed as the primary strategy.
- Chunk sizing: target 400-800 estimated tokens, max 1200 estimated tokens, 10% overlap only when a large logical block must be split.
- Every chunk must expose metadata: workspace, repository, version, package, class, method, language, framework, file path, start-line, end-line.

## Decisions Requested

### 1. Filter and extraction location

- Phase 1 currently validates ZIP entries and counts supported files, but it does not extract source files into an indexable tree.
- Phase 2 will add a dedicated extraction step inside `indexing`, after repository ZIP storage and before parsing:
  - Read `source.zip` from `RepositoryStorageService` version storage.
  - Safely extract only supported text/source files into an `extracted/` directory for that immutable repository version.
  - Reuse Phase 1 ignore rules conceptually, but centralize them in a shared `RepositoryFileFilter` so validation and indexing cannot drift.
  - Ignore `node_modules`, `build`, `target`, `dist`, `coverage`, `.git`, `.idea`, `.vscode`, binary/media/archive files, and unsafe paths.
  - Detect likely binary files during extraction by extension plus a small byte scan for NUL/control-heavy content.
- Rationale: validation is not enough for Phase 2; parsing needs actual file content and stable paths. Keeping extraction in `indexing` preserves Phase 1 upload as metadata validation and storage.

### 2. Async indexing job mechanism

- Initial implementation: Spring `@Async` backed by a named `ThreadPoolTaskExecutor` such as `indexingTaskExecutor`.
- Trigger point: after upload transaction commits and the ZIP is stored, call an indexing application service to enqueue processing for the repository version.
- Status flow:
  - `UPLOADING`: repository row created.
  - `VALIDATING`: ZIP validation/storage in progress.
  - `INDEXING`: async parser/chunk/embedding/vector storage running.
  - `READY`: all chunks and embeddings stored.
  - `FAILED`: failure reason recorded.
- Retryability:
  - Add an explicit backend retry service/endpoint for repositories in `FAILED`.
  - Retry deletes partial chunks/embeddings for the same repository version and re-runs indexing.
  - Failure always records a reason and timestamp so the repository is not stuck in an unclear state.
- Restart durability:
  - Plain `@Async` does not survive a server restart while work is in memory.
  - To satisfy reliable retry without overbuilding a queue, Phase 2 will persist indexing state in database fields and add startup recovery that finds repositories left in `INDEXING` and marks them `FAILED` with a clear retryable reason such as "Indexing interrupted by server restart."
  - User-approved MVP decision: do not implement DB-backed job/outbox in Phase 2.
  - A durable DB-backed job/outbox table must be documented as a known limitation/next step for CV/production hardening, not silently omitted.
- Rationale: this keeps MVP small and observable while avoiding the false promise that in-memory async is durable.

### 3. Migration schema mechanism

- Use Flyway migrations.
- Phase 1 schema is currently managed by Hibernate `ddl-auto`.
- Add `org.flywaydb:flyway-core` and set `spring.jpa.hibernate.ddl-auto=validate` for non-test profiles once migrations cover the current schema.
- Add migration files under `backend/src/main/resources/db/migration/`.
- Baseline strategy:
  - Generate `V1__phase1_schema.sql` from the actual Phase 1 schema, not by hand from memory.
  - Preferred source: run a local PostgreSQL dev database with current Phase 1 app, let Hibernate create/update the schema, then capture `pg_dump --schema-only` and normalize it into Flyway migration SQL.
  - Fallback source if PostgreSQL dev DB is unavailable: use Hibernate schema export from the current entities, then compare it against an actual PostgreSQL schema as soon as Postgres is available.
  - Add Phase 2 schema as `V2__repository_processing.sql`.
  - For an existing non-empty dev database with Phase 1 data, enable `spring.flyway.baseline-on-migrate=true` and `spring.flyway.baseline-version=1`, so Flyway records the existing Phase 1 schema as baseline and then applies V2.
  - For a fresh database, Flyway runs V1 then V2.
  - Hibernate must validate only in non-test runtime after the Flyway handoff; it must not continue updating the same schema in parallel.
  - Baseline verification must compare structure, not only "migration ran": run baseline+V2 against a DB with real Phase 1 data, then describe/query table columns, constraints, indexes, and foreign keys and compare them with the Hibernate-managed Phase 1 schema plus expected Phase 2 additions.
- Phase 2 migration responsibilities:
  - Create/enable `vector` extension for PostgreSQL where supported.
  - `V2__repository_processing.sql` must run `CREATE EXTENSION IF NOT EXISTS vector` before any `vector(1536)` column is created.
  - Add `source_files`, `code_chunks`, and `chunk_embeddings`.
  - Add metadata/filter indexes and vector similarity index.
- Test profile may keep H2 `create-drop` for controller/unit tests, with pgvector-specific integration checks gated behind PostgreSQL/pgvector availability.
- If no real PostgreSQL dev instance with Phase 1 data is available for baseline verification, hs-verify must report the baseline verification as skipped/blocked by missing environment; it must not silently omit it.
- Rationale: raw SQL scripts are too easy to skip; Liquibase is heavier than needed for this MVP. Flyway is the smallest reliable migration path, but the Hibernate-to-Flyway handoff must be explicit to avoid two schema owners.

### 4. Parser and chunking support by language

- Java: add `javaparser-core`; detect package, class/interface/enum/record, method/constructor boundaries from AST ranges.
- TypeScript/JavaScript: brace-aware heuristic scanner for `class`, `function`, exported functions, arrow functions assigned to const, and React-style components. This is a conscious MVP compromise because no backend TS parser exists in the classpath.
- SQL: statement-level logical chunks with optional `jsqlparser` if dependency resolution is acceptable; otherwise a semicolon splitter aware of strings/comments.
- Markdown: heading-based sections preserving fenced code blocks.
- YAML: top-level and second-level key sections using SnakeYAML declared directly if needed.
- JSON: Jackson tree/path-based chunks.
- XML: JDK DOM/SAX element/path-based chunks.
- HTML/CSS: not first-class MVP targets. Extract only as generic text/logical blocks if present in supported uploads; no dedicated AST guarantees.
- Rationale: spec supports Java/Spring Boot and React/TypeScript as MVP constraints. Java gets AST precision; TS/JS gets practical logical boundaries; config/docs get logical sectioning. HTML/CSS is cut back to avoid widening scope beyond the repository processing core.

## Task 1: Parser and semantic chunker

- Spec: SRS FR-012 to FR-014, RAG-002, RAG-006; SDS Sections 16, 17, 18, 26, 27.
- Files: `backend/pom.xml`, `backend/src/main/java/com/aiassistant/indexing/**`, parser/chunker tests.
- Do:
  - Add file filtering/extraction from stored ZIP into immutable version storage.
  - Implement Java AST chunking with JavaParser and fallback logical scanners for TS/JS, SQL, Markdown, YAML, JSON, XML.
  - Enforce max chunk size by splitting only at nested logical block, statement, paragraph, or safe line boundaries while preserving class/method metadata.
- Verify:
  - Unit tests proving Java class/method chunks are not split across boundaries.
  - Unit tests proving every emitted chunk has all required metadata fields.
  - Manual/automated sample run over real files from this repository, not fabricated-only fixtures.

## Task 2: Source file and chunk persistence

- Spec: SRS FR-016, RAG-002, RAG-005; SDS Sections 13, 14, 18, 28.
- Files: `backend/src/main/java/com/aiassistant/indexing/entity/**`, repositories, Flyway migrations.
- Do:
  - Persist `source_files` and `code_chunks`.
  - Store workspace/repository/version linkage and metadata needed for citations/filtering.
  - Make repository version indexing idempotent by clearing partial rows for the target version before retry.
- Verify:
  - Generate or capture V1 from the real Phase 1 schema and record the source command/evidence.
  - Run Flyway baseline-on-migrate on a dev DB containing Phase 1 data, apply V2, and compare table columns/constraints/indexes/foreign keys against the Hibernate-created schema plus expected Phase 2 additions.
  - If no PostgreSQL dev instance with real Phase 1 data is available, report this baseline verification as skipped/blocked due to missing environment in hs-verify; do not omit it silently.
  - Repository tests for source file/chunk persistence and metadata completeness.
  - Retry test proving partial rows do not duplicate after a failed/retried run.

## Task 3: Embedding client

- Spec: SRS FR-015, FR-041 to FR-043; AI-006; SDS Sections 23, 28, 42.
- Files: `backend/src/main/java/com/aiassistant/embedding/**`, backend config, tests.
- Do:
  - Add backend-only OpenRouter embedding client using `OPENROUTER_BASE_URL`, `OPENROUTER_API_KEY`, `OPENROUTER_EMBEDDING_MODEL`, timeout, and retry config.
  - Default model: `openai/text-embedding-3-small`, dimension `1536`.
  - Validate configured model against OpenRouter embeddings model support where possible; fail with meaningful retryable error if unsupported.
- Verify:
  - Unit tests for request/response/error mapping.
  - One real embedding endpoint call during hs-verify if `OPENROUTER_API_KEY` is available and network approval is granted; report rate-limit/provider/model failures clearly.

## Task 4: pgvector storage

- Spec: SRS FR-016, RAG-002, RAG-003; SDS ADR-004, Sections 18, 28, 29.
- Files: Flyway migrations, `backend/src/main/java/com/aiassistant/embedding/**`, `backend/src/main/java/com/aiassistant/retrieval/**` if needed for smoke search.
- Do:
  - Add `chunk_embeddings` table with `vector(1536)`, model id, dimension, and chunk foreign key.
  - Add vector similarity index, preferably `ivfflat` cosine for MVP PostgreSQL compatibility, plus metadata B-tree indexes.
  - Keep metadata in relational tables and link vectors by `chunk_id`.
- Verify:
  - Migration validation against PostgreSQL/pgvector when available.
  - Confirm `V2__repository_processing.sql` contains `CREATE EXTENSION IF NOT EXISTS vector` before any `vector(1536)` column definition.
  - Persistence test or repository-level integration test storing embeddings for indexed chunks.

## Task 5: Async indexing lifecycle and retry

- Spec: SRS US-002, FR-012 to FR-016, NFR-005; SDS Sections 15, 16, 19, DD-001, ADR-009.
- Files: `backend/src/main/java/com/aiassistant/repository/**`, `backend/src/main/java/com/aiassistant/indexing/**`, API tests.
- Do:
  - Wire upload to status transitions and async indexing service.
  - Add retry behavior for `FAILED` repositories.
  - Add startup recovery for interrupted `INDEXING` jobs by marking them failed with a retryable reason.
  - Document `@Async` non-durability and DB-backed job/outbox as a known limitation/next step.
- Verify:
  - End-to-end test: upload one sample repo and observe `UPLOADING -> VALIDATING -> INDEXING -> READY`.
  - Failure/retry test: induced indexing failure records reason and can be retried.
  - Restart-recovery test: simulate a server restart/interrupted worker while a repository is in `INDEXING`, run startup recovery, and assert status becomes `FAILED` with the expected retryable reason.

## Key Risks And Mitigations

- In-memory async is not durable across restart. Mitigation: persist status/reason, add tested startup interruption recovery, keep explicit retry, and document durable DB job/outbox as a known limitation/next step for CV/production hardening.
- TypeScript/JavaScript heuristic parser can miss complex syntax. Mitigation: keep Java AST precise, test common React/TS patterns, and document Tree-sitter/Babel sidecar as future improvement.
- pgvector cannot run on H2. Mitigation: keep unit tests H2-compatible for parser/chunk metadata, and gate real pgvector verification behind PostgreSQL availability.
- OpenRouter embeddings may fail due to missing key, unsupported model, quota, rate limit, or provider outage. Mitigation: backend-only config, meaningful provider errors, retry policy, and one real verification call when credentials/network are available.
- Flyway migration may conflict with existing `ddl-auto:update` schema. Mitigation: generate/capture V1 from the actual Phase 1 schema, use `baseline-on-migrate` for non-empty existing databases, verify structure equality after baseline+V2, and switch Hibernate to `validate` outside tests so Flyway is the only runtime schema mutator.
- Oversized methods/classes force internal splitting. Mitigation: split at logical nested blocks/statements and preserve original class/method metadata for all child chunks.
