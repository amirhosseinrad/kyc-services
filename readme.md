![Java](https://img.shields.io/badge/Java-17+-red?style=flat&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen?style=flat&logo=springboot)
![Axon Framework](https://img.shields.io/badge/Axon%20Framework-4.12-blue?style=flat&logo=axonframework)
![Camunda 8](https://img.shields.io/badge/Camunda-8.7-orange?style=flat&logo=camunda)
![Zeebe](https://img.shields.io/badge/Zeebe-Client%20Java-blueviolet?style=flat&logo=grpc)



#  KYC Service – Know Your Customer Automation

This service is a **KYC (Know Your Customer)** microservice designed for Tobank applications.  
It orchestrates customer identity verification by combining **Camunda 8 BPMN workflows**, **Axon CQRS/ES**, and **Spring Boot 3.5** to deliver a robust, auditable, and event-driven process.

---

##  Key Features

### **BPMN-Driven Orchestration**
Uses **Camunda 8 (Zeebe)** to model and execute the entire KYC flow:
- Document upload (front/back of ID card, shenasnameh)
- OCR extraction and data validation by third party
- Fraud checks & mismatch handling by third party
- User notifications and status updates

An exclusive gateway placed immediately after the `check-kyc-status` worker inspects the persisted `kycStatus` and jumps
directly to the first incomplete automated step. Completed stages such as consent capture or document uploads are skipped,
while the worker now publishes the Zeebe `processInstanceKey` as `processInstanceId` so every downstream job receives the
identifier even when earlier steps are bypassed.

### **Dynamic Deployment**
Supports runtime BPMN deployment through a REST API, with **hash-based deduplication** and **Zeebe deployment metadata** (deployment key, process version) stored in the database.

### **Event-Driven Architecture**
**Axon Framework** powers **CQRS & Event Sourcing** for process state tracking and auditing.

### **Status API**
Exposes endpoints to query and update the KYC process state from external services.

### **Integration-Ready**
Designed for integration with OCR, fraud detection, and customer information services.

---

##  Containerized Deployment

The repository ships with a production-oriented `Dockerfile` that builds the
Spring Boot service and bundles a PostgreSQL instance. The container uses the
database credentials from [`application.properties`](src/main/resources/application.properties)
by default and provisions the database, user, and schema automatically when the
container starts.

```bash
# Build the image
docker build -t kyc-services .

# Run the service (exposes Spring Boot on 8002 and PostgreSQL on 5432)
docker run -p 8002:8002 -p 5432:5432 kyc-services
```

Environment variables allow you to override the defaults without editing the
image:

| Variable     | Default           | Description                                             |
|--------------|-------------------|---------------------------------------------------------|
| `DB_HOST`    | `localhost`       | Host name used to build the JDBC URL.                   |
| `DB_PORT`    | `5432`            | Port exposed by the bundled PostgreSQL server.          |
| `DB_NAME`    | `kyc_services`    | Database that will be created if it does not yet exist. |
| `DB_USER`    | `postgres`        | Login role (created/updated during startup).            |
| `DB_PASSWORD`| `Amir@123456`     | Password assigned to the database role.                 |
| `DB_SCHEMA`  | `public`          | Schema ensured inside the target database.              |

All PostgreSQL data lives inside the container; mount `/var/lib/postgresql` to
persist it across restarts.

### Remote Debugging

The container can optionally expose a JDWP socket so you can attach IntelliJ or VS Code to a running instance (for example on `192.168.179.21`). Set the following environment variables when you `docker run`:

| Variable               | Default | Description                                                                 |
|------------------------|---------|-----------------------------------------------------------------------------|
| `ENABLE_REMOTE_DEBUG`  | `false` | Turn JDWP on/off.                                                           |
| `REMOTE_DEBUG_PORT`    | `5005`  | Socket the agent listens on (publish this port when running the container). |
| `REMOTE_DEBUG_SUSPEND` | `n`     | Pass `y` to make the JVM wait for the debugger before starting Spring.      |

Example:

```bash
docker run -d --name kyc-services \
  -p 8002:8002 -p 5432:5432 -p 5005:5005 \
  -e ENABLE_REMOTE_DEBUG=true \
  -e REMOTE_DEBUG_PORT=5005 \
  kyc-services:latest
```

From your IDE create a “Remote JVM Debug” configuration that attaches to `192.168.179.21:5005`.

--- 

##  Tech Stack

- **Backend:** Spring Boot 3.5 (Java 21+)
- **Workflow Engine:** Camunda 8 (Zeebe)
- **Architecture:** CQRS + Event Sourcing (Axon Framework)
- **Persistence:** JPA / Hibernate
- **Database:** PostgreSQL / Oracle
- **API:** REST (OpenAPI 3 / Swagger)
- **CI/CD:** GitLab CI with Maven build & tests

---

##  API Overview

| Method | Endpoint           | Description                                      |
|-------|--------------------|--------------------------------------------------|
| POST  | `/kyc/start`       | Start a new KYC process instance                 |
| DELETE| `/kyc/cancel`      | Cancel a running KYC process instance          |
| POST  | `/kyc/card/status` | Get current KYC process status                   |
| POST  | `/kyc/consent`     | Record the customer's consent for the KYC terms |
| POST  | `/kyc/selfie`      | Upload a selfie image for biometric checks       |
| POST  | `/kyc/signature`   | Upload a handwritten signature image             |
| POST  | `/kyc/video`       | Upload a recorded customer video                 |
| POST  | `/kyc/booklets`    | Upload 1–4 ID booklet pages                      |
| POST  | `/kyc/card`        | Upload front/back images of the national card    |
| POST  | `/bpmn/deploy`     | Deploy a BPMN file (multipart upload)            |

### Process Cancellation

`DELETE /kyc/process/{processInstanceId}` immediately stops the underlying Camunda workflow and
marks the persisted KYC instance as `PROCESS_CANCELLED`. The endpoint responds with `202 Accepted`
and returns the `processInstanceId`, the new `status`, and the `canceledAt` timestamp. Unknown
identifiers trigger `404 Not Found`, while workflow cancellation failures surface as
`400 Bad Request` with a localized error message.

## Error contract

Every error emitted by the HTTP API follows a shared JSON contract:

```json
{
  "code": "KYC-001",
  "message": "Validation failed",
  "details": {
    "fieldErrors": {
      "nationalCode": ["nationalCode is required"]
    }
  }
}
```

- `code` – machine-readable error identifier. The value is stable and designed for client-side branching.
- `message` – human readable description of the failure. When possible it echoes the specific validation or domain message.
- `details` – optional object that provides additional context (e.g. field level validation messages).

| Error code | HTTP status | Scenario | Notes |
|------------|-------------|----------|-------|
| `KYC-001`  | `400 Bad Request` | Validation failure (`IllegalArgumentException`, bean validation, constraint violations) | `details.fieldErrors` and/or `details.violations` list offending fields. |
| `KYC-002`  | `404 Not Found` | Referenced resource is missing | Used when a `processInstanceId` cannot be resolved. |
| `KYC-003`  | `409 Conflict` | Command rejected without a recognised cause | Returned when Axon reports a command failure that is neither a validation error nor a missing resource. |
| `KYC-004`  | `400 Bad Request` | File processing failure | Indicates uploaded multipart content could not be read. |
| `KYC-999`  | `500 Internal Server Error` | Unexpected server error | Reserved for uncaught exceptions. |

Controllers no longer craft ad-hoc error bodies. They throw meaningful exceptions (`IllegalArgumentException`, `ResourceNotFoundException`, `FileProcessingException`, etc.) and the [`GlobalExceptionHandler`](src/main/java/ir/ipaam/kycservices/application/api/error/GlobalExceptionHandler.java) formats the response using the mapping above. Existing clients should update their error handling logic to inspect the new `code`/`message` fields instead of the legacy `error` property.

### Consent Acceptance

`POST /kyc/consent` expects an `application/json` payload:

```json
{
  "processInstanceId": "<existing process instance>",
  "termsVersion": "v1.0",
  "accepted": true
}
```

- `accepted` must be `true`; the controller rejects any other value with `400 Bad Request`.
- The `processInstanceId` must already exist in the KYC store. Unknown IDs trigger a `404 Not Found` response.
- On success the service dispatches an `AcceptConsentCommand` and returns `202 Accepted` with:
  - `processInstanceId` – normalized identifier
  - `termsVersion` – normalized terms identifier
  - `status` – always `CONSENT_ACCEPTED`
- Error handling:
  - `400 Bad Request` for validation issues (`accepted` false, missing fields) or command rejections
  - `404 Not Found` when the process instance cannot be located
  - `500 Internal Server Error` for unexpected command failures

### Booklet Page Upload

> **Prerequisite for uploads:** All document-upload endpoints (`/kyc/booklets`, `/kyc/documents/card`, `/kyc/selfie`, `/kyc/signature`, `/kyc/video`) require the referenced `processInstanceId` to exist. Requests referencing an unknown instance return `404 Not Found`.

`POST /kyc/booklets` accepts up to **four multipart files** named `pages` plus a `processInstanceId` field. Each file must contain the binary payload for a booklet page (≤2 MB).

- The controller validates the number of pages (1–4) and enforces the 2 MB per-page limit. Missing pages or oversized files return `400 Bad Request`.
- The service verifies the process instance before queuing an `UploadIdPagesCommand`; unknown IDs result in `404 Not Found`.
- Success response (`202 Accepted`) body:
  - `processInstanceId`
  - `pageCount`
  - `pageSizes` – byte length for each page
  - `status` – `ID_PAGES_RECEIVED`
- Error handling:
  - `400 Bad Request` for validation failures, unreadable files, or command rejections
  - `404 Not Found` when the process instance cannot be located
  - `500 Internal Server Error` for unexpected processing errors

### Card Document Upload

`POST /kyc/documents/card` consumes `multipart/form-data` with three parts: `frontImage`, `backImage`, and `processInstanceId`.

- Both images are required and individually limited to **2 MB**. Missing or oversized parts lead to `400 Bad Request`.
- The controller confirms the process instance exists before dispatching `UploadCardDocumentsCommand`; missing instances yield `404 Not Found`.
- Success response (`202 Accepted`) body:
  - `processInstanceId`
  - `frontImageSize`
  - `backImageSize`
  - `status` – `CARD_DOCUMENTS_RECEIVED`
- Error handling mirrors the other upload endpoints: `400` for validation or command rejections, `404` for unknown processes, and `500` for internal failures.

### Selfie Upload

`POST /kyc/selfie` consumes `multipart/form-data` with a `selfie` file (≤2 MB) and a `processInstanceId` string part.

- The selfie file is required and validated for emptiness and size; violations return `400 Bad Request`.
- The controller checks that the process instance exists, returning `404 Not Found` if it does not.
- On success (`202 Accepted`), the body includes:
  - `processInstanceId`
  - `selfieSize`
  - `status` – `SELFIE_RECEIVED`
- Errors: `400` for validation issues or command rejections, `404` for missing process instances, and `500` for unexpected processing failures.

### Signature Upload

`POST /kyc/signature` consumes `multipart/form-data` with a `signature` image (≤2 MB) and a `processInstanceId` string part.

- The signature file must be provided and within the size limit; otherwise the controller returns `400 Bad Request`.
- The process instance is validated before dispatching `UploadSignatureCommand`; unknown IDs receive `404 Not Found`.
- Successful requests (`202 Accepted`) respond with:
  - `processInstanceId`
  - `signatureSize`
  - `status` – `SIGNATURE_RECEIVED`
- Errors follow the standard pattern: `400` for validation problems or command rejections, `404` when the process instance is missing, and `500` for unexpected failures.

### Video Upload

`POST /kyc/video` consumes `multipart/form-data` with a `video` file (≤10 MB) and a `processInstanceId` part.

- The video file must be provided and within the size limit; otherwise the controller returns `400 Bad Request`.
- The endpoint verifies the process instance before sending `UploadVideoCommand`, replying with `404 Not Found` if the instance is missing.
- Success response (`202 Accepted`) body:
  - `processInstanceId`
  - `videoSize`
  - `status` – `VIDEO_RECEIVED`
- Errors follow the same pattern: `400` for validation or command rejections, `404` for unknown process instances, and `500` for unexpected failures.

---

## Localized error messages

The API returns bilingual error payloads. Message templates are loaded from the
resource configured by `error.messages.location` (see
`src/main/resources/application.properties`). The default value points to the
bundled `classpath:error-messages.json`. To override the catalogue, provide an
external resource using any Spring resource URI, for example:

```
error.messages.location=file:/etc/kyc/error-messages.json
```

Each entry in the JSON file must expose both English (`en`) and Persian (`fa`)
strings:

```json
{
  "error.some.key": { "en": "English text", "fa": "متن فارسی" }
}
```

Operators can add new keys or override existing ones by editing the external
file and reloading the application.
