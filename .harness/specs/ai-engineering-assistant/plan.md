# Plan: Phase 1 Foundation

**Status:** draft

**Baseline:** `git status --short` -> fails because this workspace is not currently recognized as a Git repository; `backend/` and `frontend/` contain only `.gitkeep`; `PROJECT_CONTEXT.md` referenced by `README.md` is missing.

## Task 1: Backend project foundation
- Spec: SDS Sections 5, 6, 11, 38, 39, 40, 41, 47; SRS FR-001 to FR-011, NFR-004
- Files: `backend/pom.xml`, `backend/src/main/java/com/aiassistant/**`, `backend/src/main/resources/application.yml`, `backend/src/test/java/com/aiassistant/**`
- Do:
  - Create a Java 21 Spring Boot Maven app with layered packages, validation, Spring Security, JPA, PostgreSQL-ready config, and H2 test profile for local verification.
  - Add global error responses matching the SDS shape.
  - Keep AI/OpenRouter out of Phase 1 except configuration placeholders if needed by the app scaffold.
- Verify: `cd backend; ./mvnw test` or `cd backend; mvn test` depending on available wrapper/tooling.

## Task 2: Authentication and token lifecycle
- Spec: SRS FR-001 to FR-004; SDS Sections 38, 39, 40
- Files: `backend/src/main/java/com/aiassistant/auth/**`, `backend/src/main/java/com/aiassistant/config/**`, auth tests
- Do:
  - Implement register, login, refresh, logout, and current profile endpoints under `/api/v1`.
  - Store BCrypt password hashes and persisted refresh tokens with expiry and revocation state.
  - Use short-lived JWT access tokens with user id, email, and role claims; validate bearer tokens through Spring Security.
- Verify: service/controller tests for registration uniqueness, login success/failure, refresh token rotation/reuse behavior, logout revocation, and `/users/me` authorization.

## Task 3: Workspace management
- Spec: SRS FR-005 to FR-007, BR-001, BR-002; SDS Sections 14, 38, 39
- Files: `backend/src/main/java/com/aiassistant/workspace/**`, workspace tests
- Do:
  - Implement workspace CRUD with required fields: name, description, programming language, framework, visibility.
  - Enforce owner-only access for read/update/delete.
  - Cascade delete Phase 1 repository metadata and local upload directory references owned by the workspace.
- Verify: API/service tests for owner filtering, create/update validation, forbidden cross-user access, and delete behavior.

## Task 4: Repository ZIP upload and metadata
- Spec: SRS FR-008 to FR-011, FR-012/FR-013 as validation boundaries for upload, BR-001, BR-003, BR-007; SDS Sections 15, 16, 19, 38, 40
- Files: `backend/src/main/java/com/aiassistant/repository/**`, `backend/src/main/java/com/aiassistant/indexing/**`, repository upload tests
- Do:
  - Implement `POST /api/v1/workspaces/{id}/repositories`, `GET /api/v1/repositories/{id}`, `GET /api/v1/repositories/{id}/status`, and `DELETE /api/v1/repositories/{id}`.
  - Accept ZIP only, create repository/version records, compute metadata: language, framework, build tool/package manager, file count, repository size.
  - Return promptly with status transitioning through `UPLOADING`, `VALIDATING`, then `INDEXING` or `FAILED`; Phase 2 will fill the actual indexing pipeline, so Phase 1 should expose a clear placeholder boundary without embeddings/chunks.
  - Safely extract/inspect ZIPs into local storage with path traversal protection and ignore rules for unsupported/binary directories.
- Verify: upload tests for valid ZIP, unsupported archive, oversized archive, unsupported language, zip-slip entries, excessive compression ratio, excessive expanded size/file count/depth, and workspace ownership.

## Task 5: Frontend Phase 1 user flow
- Spec: SDS Sections 5, 7, 38, 39; SRS user journey through upload
- Files: `frontend/package.json`, `frontend/src/**`, frontend tests if practical
- Do:
  - Create a React + TypeScript + Vite app with Axios, React Router, TanStack Query, Tailwind CSS, auth pages, workspace list/detail forms, and repository upload/status UI.
  - Store access token client-side only as needed for API calls and call backend refresh/logout endpoints through an API client abstraction.
  - Keep the first screen as the usable app flow, not a marketing page.
- Verify: `cd frontend; npm test -- --run` if tests are added, plus `cd frontend; npm run build`.

## Task 6: Integration, docs, and handoff checks
- Spec: SDS Sections 44, 46, 47
- Files: `README.md`, optional `infra/docker-compose.yml`, `.env.example` files only if needed
- Do:
  - Document local run commands, required environment variables, and Phase 1 API endpoints.
  - Add Docker/PostgreSQL wiring only if it does not distract from the Phase 1 implementation; otherwise keep H2-backed test verification and PostgreSQL-ready config.
  - Preserve clear boundaries for Phase 2 indexing and Phase 3 AI work.
- Verify: backend tests, frontend build/tests, and a manual smoke path where feasible: register -> login -> create workspace -> upload ZIP -> poll status.

## Key Risks And Mitigations

- JWT refresh flow: refresh token reuse can create session fixation or stolen-token persistence. Mitigation: persist refresh tokens hashed or at least opaque, bind them to user/session metadata, rotate on every refresh, revoke previous token on logout, reject expired/revoked tokens, and add tests for stale-token reuse.
- ZIP bomb and zip-slip uploads: compressed files can exhaust disk/memory or write outside storage. Mitigation: stream entries, normalize destination paths, cap compressed size, expanded size, entry count, directory depth, per-file size, compression ratio, and nested archives; never trust entry names.
- Repository status honesty: Phase 1 cannot truly index embeddings/chunks. Mitigation: expose `INDEXING` placeholder/status boundary and document that Phase 2 owns parser/chunk/embedding completion, avoiding fake `READY` semantics unless only metadata validation is represented.
- Ownership enforcement: repository endpoints can leak project metadata across users. Mitigation: every workspace/repository lookup is scoped by authenticated owner and covered by forbidden-access tests.
- Frontend token handling: browser storage can leak JWTs under XSS. Mitigation for MVP: avoid exposing refresh tokens to JS if backend cookie support is practical; otherwise keep storage narrow, centralize API auth handling, and add clear TODO/security boundary before production hardening.
- Scope creep: Phase 1 should not implement RAG, embeddings, OpenRouter, documentation generation, or code review. Mitigation: add interfaces/placeholders only where needed to preserve roadmap boundaries.
