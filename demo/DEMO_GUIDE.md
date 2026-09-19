# SchemaBridge AI - Enterprise System A <-> System B Demo Guide

This guide explains **what each system is for**, the **data payloads for both Write and Read flows**, and **step-by-step instructions on how to demonstrate SchemaBridge AI** to stakeholders, executives, or technical evaluators.

---

## 1. Conceptual Overview: What is for What?

| Component | Role | Why It Exists & Characteristics |
| :--- | :--- | :--- |
| **System A** | **Legacy Core POS & Billing Engine** (Internal On-Premise System) | Built 10+ years ago. Employs legacy naming standards: flat structures, `snake_case` keys, `DD/MM/YYYY` slash dates, integer/string booleans (`"Yes"` / `"No"`), and legacy product codes. Cannot be refactored easily without breaking 50+ internal legacy services. |
| **System B** | **Modern Cloud ERP / Salesforce Platform** (Target System of Record) | Modern cloud SaaS API. Enforces strict typed JSON Schemas: nested objects, `camelCase` keys, ISO-8601 timestamps (`YYYY-MM-DD`), native booleans (`true`/`false`), and canonical 3-letter enterprise enum codes (`"GLD"`). |
| **SchemaBridge AI** | **Intelligent Bi-Directional Connector Proxy** | Sits transparently between System A and System B. <br>• **At Design Time**: Uses OpenText Aviator ADT + 5-Level Matching to inspect sample schemas and automatically generate high-confidence mappings.<br>• **At Runtime**: Executes 100% deterministically in pure Java (<5ms). No production payloads or PII ever touch an LLM. |

---

## 2. The Bi-Directional Architecture: Read & Write

```
========================================================================================
WRITE FLOW: System A creates a record in System B
========================================================================================
[System A: Legacy POS] 
         |
         | 1. Emits legacy payload (order_num, name, dob: "15/08/1988", active: "Yes")
         v
[SchemaBridge AI Proxy]
         |
         | 2. Executes 9 deterministic rules in pure Java (<5ms)
         | 3. Validates against System B's JSON Schema
         v
[System B: Cloud ERP]
         | 4. Receives modern payload (orderId, userName, dateOfBirth: "1988-08-15", true)
         | 5. Persists record and returns acknowledgment (201 Created)


========================================================================================
READ FLOW: System A queries customer/order details from System B
========================================================================================
[System A: Legacy POS] 
         ^
         | 4. Receives legacy formatted payload with ZERO code changes needed!
         |
[SchemaBridge AI Proxy]
         ^
         | 3. Intercepts response & executes reverse deterministic mapping (<5ms)
         |
[System B: Cloud ERP]
         | 1. Queries database and emits modern enterprise record
         | 2. Returns modern JSON (orderId, userName, dateOfBirth: "1988-08-15")
```

---

## 3. Data Payloads: Side-by-Side Comparison

### Flow 1: WRITE Operation (System A &rarr; SchemaBridge AI &rarr; System B)

#### System A Input (What System A sends):
```json
{
  "order_num": "ORD-2026-991",
  "cust_id": "CUST-4081",
  "name": "Dr. Sarah Connor",
  "dob": "15/08/1988",
  "active": "Yes",
  "tier": "GOLD_TIER",
  "contact": {
    "email": "sarah.c@cyberdyne.org",
    "phone": "+1-555-0199"
  },
  "total_amount": 499.99
}
```

#### SchemaBridge Applied Rules:
| Source Field (System A) | Transformation Operation | Target Field (System B) | Rule Parameters |
| :--- | :--- | :--- | :--- |
| `order_num` | `RENAME` | `orderId` | - |
| `cust_id` | `RENAME` | `customerId` | - |
| `name` | `RENAME` / `SYNONYM` | `userName` | - |
| `dob` | `DATE_FORMAT` | `dateOfBirth` | `"sourceFormat": "dd/MM/yyyy"`, `"targetFormat": "yyyy-MM-dd"` |
| `active` | `STRING_TO_BOOLEAN` | `accountEnabled` | `"trueValues": ["Yes", "Y", "true", "1"]` |
| `tier` | `ENUM_MAP` | `membershipLevel` | `"mappings": {"GOLD_TIER": "GLD", "SILVER_TIER": "SLV"}` |
| `contact.email` | `FLATTEN` | `emailAddress` | - |
| `contact.phone` | `FLATTEN` | `telephone` | - |
| `total_amount` | `RENAME` | `totalAmount` | - |

#### System B Received Output (What SchemaBridge forwards to System B):
```json
{
  "orderId": "ORD-2026-991",
  "customerId": "CUST-4081",
  "userName": "Dr. Sarah Connor",
  "dateOfBirth": "1988-08-15",
  "accountEnabled": true,
  "membershipLevel": "GLD",
  "emailAddress": "sarah.c@cyberdyne.org",
  "telephone": "+1-555-0199",
  "totalAmount": 499.99
}
```

---

### Flow 2: READ Operation (System B &rarr; SchemaBridge AI &rarr; System A)

#### System B Emitted Record (What System B returns):
```json
{
  "orderId": "ORD-2026-991",
  "customerId": "CUST-4081",
  "userName": "Dr. Sarah Connor",
  "dateOfBirth": "1988-08-15",
  "accountEnabled": true,
  "membershipLevel": "GLD",
  "emailAddress": "sarah.c@cyberdyne.org",
  "telephone": "+1-555-0199",
  "totalAmount": 499.99
}
```

#### System A Received Output (What SchemaBridge delivers back to System A):
```json
{
  "order_num": "ORD-2026-991",
  "cust_id": "CUST-4081",
  "name": "Dr. Sarah Connor",
  "dob": "15/08/1988",
  "active": "Yes",
  "tier": "GOLD_TIER",
  "contact": {
    "email": "sarah.c@cyberdyne.org",
    "phone": "+1-555-0199"
  },
  "total_amount": 499.99
}
```
*System A consumes this response directly without altering any legacy parsing code!*

---

## 4. How to Demo (3 Demonstration Modes)

### Method 1: The Automated Live Terminal Demo (Fastest & Most Visual)
Run the pre-configured terminal runner script:
```powershell
python demo/run_demo.py
```
*(Or via PowerShell: `.\demo\run_demo.ps1`)*

**What this shows**:
1. Prints colorful, structured side-by-side payloads for both Write and Read.
2. Displays real-time duration (<5ms).
3. Lists every single applied rule and JSON Schema validation status.
4. Auto-detects if the backend is running live or runs via local deterministic simulation engine.

---

### Method 2: Interactive Web UI Demo (Best for Business & Non-Technical Stakeholders)

1. **Start the Frontend & Backend**:
   ```powershell
   # Terminal 1 (Backend):
   cd backend
   mvn spring-boot:run

   # Terminal 2 (Frontend):
   cd frontend
   npm start
   ```
2. **Open the Browser**: Navigate to `http://localhost:4200`.
3. **Step 1: Project Setup & Importer**:
   - Click **"Setup & Importer"** or create a new project.
   - In the sample dropdown, select:
     `"Enterprise Core POS (System A) -> Cloud ERP (System B) [WRITE FLOW]"`.
   - Click **"Load Sample"**.
   - Notice how System A's fields and System B's target schema are instantly extracted and parsed into hierarchical tree nodes with data types.
4. **Step 2: 3-Column Mapping Workspace**:
   - Navigate to the **"Mapping Workspace"**.
   - See the 3-column layout:
     - Left: Source Fields (System A)
     - Center: Mapping Cards with confidence indicators (e.g. 98% High, 95% High) and operation dropdowns (`DATE_FORMAT`, `STRING_TO_BOOLEAN`, `ENUM_MAP`).
     - Right: Target Fields (System B).
   - Test the **Natural Language Prompt Box**:
     - Type: *"Convert dob from dd/MM/yyyy to yyyy-MM-dd"* and click **"Apply NL Rule"**.
     - OpenText Aviator ADT parses the prompt into a structured `DATE_FORMAT` operation!
5. **Step 3: Transformation Lab**:
   - Navigate to **"Transformation Lab"**.
   - Click **"Run Transformation"**.
   - Watch the live deterministic Java engine transform the payload in **2ms**.
   - Show the green **"Validation Passed"** badges confirming strict schema adherence.
6. **Step 4: Versioning & Audit Trail**:
   - Click **"Publish v1.0"**.
   - Switch to the **"Audit View"** to show enterprise compliance: every mapping creation, approval, and execution has a cryptographic MDC correlation ID and audit timestamp.

---

### Method 3: Direct REST API Demo (Best for Integration Architects)

Execute the live endpoints directly via PowerShell or cURL:

#### 1. Inspect Demo Scenario & Schemas:
```powershell
curl.exe -X GET http://localhost:8080/api/demo/scenario
```

#### 2. Execute Live WRITE Transformation:
```powershell
curl.exe -X POST http://localhost:8080/api/demo/write `
  -H "Content-Type: application/json" `
  -d '{"order_num":"ORD-2026-991","name":"Dr. Sarah Connor","dob":"15/08/1988","active":"Yes","tier":"GOLD_TIER","contact":{"email":"sarah.c@cyberdyne.org","phone":"+1-555-0199"},"total_amount":499.99}'
```

#### 3. Execute Live READ Transformation:
```powershell
curl.exe -X POST http://localhost:8080/api/demo/read `
  -H "Content-Type: application/json" `
  -d '{"orderId":"ORD-2026-991","userName":"Dr. Sarah Connor","dateOfBirth":"1988-08-15","accountEnabled":true,"membershipLevel":"GLD","emailAddress":"sarah.c@cyberdyne.org","telephone":"+1-555-0199","totalAmount":499.99}'
```

#### 4. One-Click Seed into UI Database:
```powershell
curl.exe -X POST http://localhost:8080/api/demo/seed
```

---

## 5. Talking Points & Frequently Asked Questions

### Q1: "Are we sending sensitive customer data or financial PII to an AI model in production?"
> **Answer**: **No, absolutely not.** That is the foundational architectural principle of SchemaBridge AI. 
> The AI (OpenText Aviator ADT) is strictly **advisory** and used exclusively at **design time** to recommend field pairings from schema definitions or sample files. 
> Once approved, transformations run **100% deterministically in pure Java** on your own servers. Production payloads never leave your network and never touch an LLM.

### Q2: "What happens if System B adds a new required field or changes a field format next week?"
> **Answer**: SchemaBridge AI features automatic **Schema Drift & Change Detection** (`/api/projects/{id}/schemas/diff`). When System B updates its schema, SchemaBridge detects breaking changes, flags affected mapping rules, and prompts Aviator to propose migration patches before downstream services break.

### Q3: "What is the performance overhead of routing through SchemaBridge?"
> **Answer**: The transformation engine is written in native Java using high-performance Jackson AST trees. Typical transformation latency is **under 3 milliseconds**, making it suitable for high-throughput enterprise API gateways.
