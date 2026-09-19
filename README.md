# SchemaBridge AI

> **Intelligent translation between any data schema, payload format, and business vocabulary.**

SchemaBridge AI is an enterprise intelligent connector platform that maps, translates, and validates data produced by **System A** into the format required by **System B**.

---

## Architectural Principles

1. **AI Advisory Only, Deterministic Execution in Production**:
   - Production transformations are **never sent to an LLM**.
   - Transformations are executed **100% deterministically** in pure Java using approved, versioned mapping rules.
   - AI (OpenText Aviator ADT) is used **only** during:
     - Initial mapping recommendations for ambiguous or unresolved fields.
     - Natural language plain-English rule translation.
     - Semantic similarity explanations.
     - Schema change impact analysis.

2. **5-Level Matching Pipeline**:
   - **Level 1 (Exact Matching)**: Identical field names (e.g. `projectId` &rarr; `projectId`).
   - **Level 2 (Normalised Matching)**: Case insensitivity, snake_case, camelCase, PascalCase, stripping technical prefixes/suffixes (e.g. `project_id` &rarr; `projectId`, `user_name` &rarr; `userName`).
   - **Level 3 (Synonym Matching)**: Persisted configurable synonym dictionary (e.g. `dob` &rarr; `dateOfBirth`, `active` &rarr; `accountEnabled`).
   - **Level 4 (Historical Matching)**: Recommends previously approved rules within the domain.
   - **Level 5 (AI Semantic Matching via Aviator ADT)**: Unresolved fields are evaluated with OpenText Aviator ADT using prompt safety guardrails and strict JSON schema responses.
   - **Graceful Fallback**: If Aviator ADT is offline or unreachable, Levels 1–4 continue seamlessly and transformations remain unaffected.

3. **20 Supported Deterministic Operations**:
   - `RENAME`, `STRING_TO_NUMBER`, `NUMBER_TO_STRING`, `STRING_TO_BOOLEAN`, `BOOLEAN_TO_STRING`
   - `DATE_FORMAT`, `ENUM_MAP`, `DEFAULT_VALUE`, `CONSTANT_VALUE`, `COPY`, `CONCAT`, `SPLIT`
   - `FLATTEN`, `NEST`, `ARRAY_MAP`, `CONDITIONAL`, `REMOVE`, `TRIM`, `UPPERCASE`, `LOWERCASE`

---

## Technology Stack

- **Backend**: Java 21 LTS, Spring Boot 3.3.4, Maven, Spring Data JPA, PostgreSQL / H2 (zero-config local dev), JSON Schema Validator, SpringDoc OpenAPI 2.6.
- **Frontend**: Angular 21, Modern Glassmorphism & Dark Mode Design System, Inter/Outfit typography, standalone architecture.
- **AI Integration**: OpenText Content Aviator ADT (`aviator_adt/`) with Google GenAI / Vertex AI (`credentials/credentials.json`).
- **Testing**: JUnit 5, Mockito, Playwright for E2E tests.
- **Deployment**: Docker & Docker Compose.

---

## Quick Start (Local Development)

### 1. Run the Spring Boot Backend

The backend defaults to an embedded in-memory PostgreSQL-compatible mode (`local` profile) with instant startup and zero external database dependencies:

```bash
cd backend
mvn clean spring-boot:run
```

- **Backend API**: `http://localhost:8080`
- **Swagger / OpenAPI UI**: `http://localhost:8080/swagger-ui.html`
- **H2 Web Console**: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:schemabridge`)

### 2. Run the Angular Frontend

```bash
cd frontend
npm install --legacy-peer-deps
npm start
```

- **Frontend Application**: `http://localhost:4200`

### 3. Run with Docker Compose

To start the full stack (PostgreSQL + Aviator ADT + SchemaBridge Backend + Frontend + Redis):

```bash
docker compose up --build
```

---

## Live System A <-> System B Bi-Directional Demo (Ready to Run!)

Run the interactive demo runner to simulate System A (Legacy Core POS) and System B (Cloud ERP) communicating in both Write and Read flows:

```bash
# Run the automated live terminal demo:
python demo/run_demo.py
```
*(Or via PowerShell: `.\demo\run_demo.ps1`). See [demo/DEMO_GUIDE.md](file:///c:/Users/baljinders/Documents/GitHub/SchemaBridgeAI/demo/DEMO_GUIDE.md) for full speaker scripts, talking points, and UI walkthrough steps.*

---

## Sample Workflow: HR to Enterprise Directory

### Input Source Payload:
```json
{
  "name": "Baljinder Singh",
  "dob": "10/05/1992",
  "active": "Yes",
  "project_id": 1001,
  "contact": {
    "email": "user@example.com"
  }
}
```

### Target Schema Definition:
```json
{
  "userName": "string",
  "dateOfBirth": "yyyy-MM-dd",
  "accountEnabled": "boolean",
  "projectId": "string",
  "emailAddress": "string"
}
```

### Generated Mappings:
| Source Path | Target Path | Operation | Confidence | Match Method |
| :--- | :--- | :--- | :--- | :--- |
| `name` | `userName` | `RENAME` | 93% (HIGH) | `SYNONYM` |
| `dob` | `dateOfBirth` | `DATE_FORMAT` (`dd/MM/yyyy` &rarr; `yyyy-MM-dd`) | 95% (HIGH) | `SYNONYM` |
| `active` | `accountEnabled` | `STRING_TO_BOOLEAN` | 93% (HIGH) | `SYNONYM` |
| `project_id` | `projectId` | `NUMBER_TO_STRING` | 95% (HIGH) | `NORMALISED` |
| `contact.email` | `emailAddress` | `RENAME` | 95% (HIGH) | `AI_SEMANTIC` |

### Deterministic Transformed Output:
```json
{
  "userName": "Baljinder Singh",
  "dateOfBirth": "1992-05-10",
  "accountEnabled": true,
  "projectId": "1001",
  "emailAddress": "user@example.com"
}
```

---

## API Examples (cURL)

### 1. Create an Integration Project
```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "HR to Enterprise Directory",
    "description": "Employee synchronization pipeline",
    "sourceSystemName": "HR_System_A",
    "targetSystemName": "Enterprise_Directory_B"
  }'
```

### 2. Import Schemas
```bash
curl -X POST http://localhost:8080/api/projects/{projectId}/schemas/source \
  -H "Content-Type: application/json" \
  -d '{
    "schemaType": "SAMPLE_JSON",
    "schemaContent": "{\"name\":\"Baljinder Singh\",\"dob\":\"10/05/1992\",\"active\":\"Yes\",\"project_id\":1001,\"contact\":{\"email\":\"user@example.com\"}}"
  }'
```

### 3. Generate 5-Level Mapping Suggestions
```bash
curl -X POST http://localhost:8080/api/projects/{projectId}/mappings/generate
```

### 4. Execute Deterministic Transformation
```bash
curl -X POST http://localhost:8080/api/projects/{projectId}/transform/execute \
  -H "Content-Type: application/json" \
  -d '{
    "sourcePayload": {
      "name": "Baljinder Singh",
      "dob": "10/05/1992",
      "active": "Yes",
      "project_id": 1001,
      "contact": { "email": "user@example.com" }
    },
    "validateTarget": true
  }'
```

### 5. Check Aviator ADT Health
```bash
curl http://localhost:8080/api/aviator/health
```

---

## Running Tests

### Backend Unit & Integration Tests (18 Tests):
```bash
cd backend
mvn test
```

### Playwright E2E UI Tests:
```bash
cd frontend
npx playwright test
```

---

## Security & Observability

- **No Credential Exposure**: `credentials/credentials.json` is accessed solely through environment variables.
- **Structured JSON Logging**: Every log entry includes `severity`, `message`, `time`, `serviceCode`, `applicationVersion`, `logger_name`, and `request.uuid` (MDC correlation ID).
- **Prompt Safety**: AI system prompts treat all schema descriptions and values as untrusted data to guard against injection.
