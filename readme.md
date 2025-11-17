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

### Remote Deployment Script

Use `deploy-dev.sh` to push the container to the shared dev box (defaults to `devops@192.168.179.21`). The script now copies `kyc-services-image.tar` into `/home/devops/projects` on the remote host before running `docker load`, so every deployment leaves the exported image under `/home/devops/projects`.

| Variable       | Default                  | Description                                      |
|----------------|--------------------------|--------------------------------------------------|
| `REMOTE_HOST`  | `192.168.179.21`         | SSH destination                                  |
| `REMOTE_USER`  | `devops`                 | SSH user                                         |
| `REMOTE_DIR`   | `/home/<user>/projects`  | Remote folder receiving the tarball              |
| `ENABLE_REMOTE_DEBUG` | `true` (script)   | Keeps JDWP enabled on the remote container       |
| `REMOTE_DEBUG_PORT`   | `5005`             | Port published/bound for the debugger            |

```bash
# Default remote (devops@192.168.179.21) with JDWP enabled
./deploy-dev.sh

# Override remote directory/host if needed
REMOTE_DIR=/opt/kyc REMOTE_HOST=192.168.179.30 ./deploy-dev.sh
```

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

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/kyc/start` | Launch a new Camunda KYC workflow for the provided national code (returns `processInstanceId`). |
| POST | `/kyc/status` | Fetch the aggregated workflow status for a national code (`KycStatusResponse`). |
| POST | `/kyc/cancel` | Cancel an active process by `processInstanceId` and mark it as `PROCESS_CANCELLED`. |
| POST | `/kyc/consent` | Persist the customer's consent decision and emit the `CONSENT_ACCEPTED` workflow message. |
| POST | `/kyc/customer-info` | Store English first/last name, email, and telephone for the process. |
| POST | `/kyc/address` | Collect postal address/postal code (optionally gated by the `stage` header). |
| POST | `/kyc/card` | Upload front/back national card images (multipart). |
| POST | `/kyc/card/status` | Flag whether the applicant holds the new national card. |
| POST | `/kyc/card/tracking` | Record the physical card tracking number. |
| POST | `/kyc/booklets` | Upload one to four ID booklet pages (`pages[]` + `processInstanceId`). |
| POST | `/kyc/selfie` | Upload a selfie image for biometric verification. |
| POST | `/kyc/signature` | Upload a scanned handwritten signature. |
| POST | `/kyc/video` | Upload a selfie verification video plus the reference still image. |
| POST | `/kyc/documents/latest` | Download the latest stored document for a national code/document type (binary response). |
| POST | `/kyc/deploy` | Deploy a BPMN process definition when the contents differ from the last deployment. |

### Flow overview

The `kyc-process` BPMN (`src/main/resources/bpmn/kyc-process.bpmn`) wires every REST endpoint to a matching Zeebe message so the workflow can pause and resume between automated workers:

1. `check-kyc-status` service task loads the latest persisted status and routes the instance to the first unfinished receive task.
2. `Consent and Declarations` waits for `POST /kyc/consent` to publish `consent-accepted`, then `Wait for card status` blocks on `POST /kyc/card/status` (`card-status-recorded`).
3. `New National Card?` gateway uses the `card` flag to decide between:
   - Card-holders uploading both sides of the card via `POST /kyc/card` (`card-documents-uploaded`);
   - Or the fallback `record tracking number` (`POST /kyc/card/tracking`) followed by booklet pages (`POST /kyc/booklets`, message `booklet-pages-uploaded`).
   Both branches converge before the selfie (`POST /kyc/selfie`) so the workflow waits for all required documents.
4. After selfies, `POST /kyc/video` emits `video-uploaded`; the `high mismatch/confidence low` gateway either loops back for another video (notification task) or proceeds when `match=true`.
5. `collect: Zip-Code and address` subscribes to `zip-code-and-address-collected` (`POST /kyc/address`), then the `check zip code` worker validates the postal code. Invalid entries trigger another notification loop; valid addresses unlock signature and English profile.
6. `upload-signature` listens for `POST /kyc/signature`; once satisfied, `save : english firstname, lastname, email, telephone` consumes `english-personal-info-provided` (`POST /kyc/customer-info`) and fires the terminal throw event, signalling workflow completion.

### Process Cancellation

`POST /kyc/cancel` accepts a JSON body with `processInstanceId`, immediately stops the underlying Camunda workflow, and marks the persisted KYC instance as `PROCESS_CANCELLED`. The endpoint responds with `202 Accepted` and returns the `processInstanceId`, the new `status`, and the `canceledAt` timestamp. Unknown identifiers trigger `404 Not Found`, while workflow cancellation failures surface as `400 Bad Request` with a localized error message.

## Error contract

Controllers only throw typed exceptions; the [`GlobalExceptionHandler`](src/main/java/ir/ipaam/kycservices/application/api/error/GlobalExceptionHandler.java) translates them into a single JSON envelope backed by the localized catalogue in [`error-messages.json`](src/main/resources/error-messages.json).

```json
{
  "error": {
    "code": "KYC-1000",
    "en": "Validation failed",
    "fa": "اعتبارسنجی ناموفق بود"
  },
  "details": {
    "fieldErrors": {
      "nationalCode": ["nationalCode must be a valid Iranian national code"]
    },
    "globalErrors": [
      "error.workflow.acceptConsent.failed"
    ]
  }
}
```

- `error.code` – stable token you can branch on. Codes come from the message catalogue so custom deployments can add/override entries.
- `error.en` / `error.fa` – already-localized explanations for the user; both languages are always returned.
- `details` – optional context. Validation failures include `fieldErrors`, bean validation exposes `violations`, and some handlers attach `globalErrors` for cross-field issues.

The handler resolves exceptions to message keys in the following order:

| Exception / key | HTTP status | Default message key → error code | Description |
|-----------------|-------------|----------------------------------|-------------|
| `IllegalArgumentException`, `MethodArgumentNotValidException`, `ConstraintViolationException` | `400 Bad Request` | `error.validation.failed` → `KYC-1000` | Payload validation, business preconditions, constraint violations. |
| `ResourceNotFoundException` | `404 Not Found` | `error.process.notFound` → `KYC-2000` | Unknown `processInstanceId` or missing aggregate/event. |
| `FileProcessingException` | `400 Bad Request` | `error.file.read` → `KYC-3001` | Binary uploads that cannot be read/parsed. |
| `ObjectStorageUnavailableException` | `503 Service Unavailable` | `error.storage.unavailable` → `KYC-4803` | MinIO/object storage downtime. |
| `CommandExecutionException` (fallback) | `409 Conflict` | `error.command.execution` → `KYC-1002` | Axon command rejections not mapped above. |
| Any other exception | `500 Internal Server Error` | `error.unexpected` → `KYC-1999` | Last-resort handler with full stack trace logging. |

Because every response contains the structured `error` object, clients no longer need to parse legacy `message` strings. Instead, branch on `error.code`, surface `error.en` or `error.fa`, and inspect `details` for per-field issues.

### Error message catalogue

`ErrorMessageKeys` enumerates every key resolved by the handler; the same list powers [`error-messages.json`](src/main/resources/error-messages.json). Each entry maps to a bilingual payload plus the machine-readable `code`. Use this table when building client-side translations or when overriding the catalogue via `error.messages.location`.

| Message key | Code | Default English text |
|-------------|------|-----------------------|
| `error.validation.failed` | `KYC-1000` | Validation failed |
| `error.nationalCode.invalid` | `KYC-1001` | nationalCode must be a valid Iranian national code |
| `error.command.execution` | `KYC-1002` | Command execution failed |
| `error.unexpected` | `KYC-1999` | Unexpected error |
| `error.process.notFound` | `KYC-2000` | Process instance not found |
| `error.processInstanceId.required` | `KYC-2001` | processInstanceId must be provided |
| `error.process.idMismatch` | `KYC-2002` | Process instance identifier mismatch |
| `error.termsVersion.required` | `KYC-2003` | termsVersion must be provided |
| `error.consent.accepted` | `KYC-2004` | accepted must be true |
| `error.request.invalidJson` | `KYC-2005` | Request body could not be parsed |
| `error.bpmn.noFile` | `KYC-3000` | No file uploaded |
| `error.file.read` | `KYC-3001` | Unable to read uploaded file |
| `error.file.type` | `KYC-3002` | Unsupported file type |
| `error.card.front.required` | `KYC-3100` | frontImage must be provided |
| `error.card.front.size` | `KYC-3101` | frontImage exceeds maximum size |
| `error.card.back.required` | `KYC-3102` | backImage must be provided |
| `error.card.back.size` | `KYC-3103` | backImage exceeds maximum size |
| `error.card.nationalCode.mismatch` | `KYC-3104` | Uploaded card does not belong to the current customer |
| `error.selfie.required` | `KYC-3200` | selfie must be provided |
| `error.selfie.size` | `KYC-3201` | selfie exceeds maximum size |
| `error.signature.required` | `KYC-3300` | signature must be provided |
| `error.signature.size` | `KYC-3301` | signature exceeds maximum size |
| `error.video.required` | `KYC-3400` | video must be provided |
| `error.video.size` | `KYC-3401` | video exceeds maximum size |
| `error.workflow.acceptConsent.failed` | `KYC-4000` | Unable to record consent decision |
| `error.workflow.englishInfo.failed` | `KYC-4001` | Unable to save English personal information |
| `error.workflow.cardUpload.failed` | `KYC-4002` | Unable to upload national card images |
| `error.workflow.videoUpload.failed` | `KYC-4003` | Unable to upload verification video |
| `error.workflow.signatureUpload.failed` | `KYC-4004` | Unable to upload signature image |
| `error.workflow.bookletValidation.failed` | `KYC-4005` | Booklet pages were rejected by the validation service |
| `error.workflow.idUpload.failed` | `KYC-4006` | Unable to upload ID pages |
| `error.workflow.selfieUpload.failed` | `KYC-4007` | Unable to upload selfie photo |
| `error.workflow.processCancel.failed` | `KYC-4008` | Unable to cancel the workflow instance |
| `error.workflow.selfieValidation.failed` | `KYC-4009` | Selfie image failed biometric validation |
| `error.id.pages.required` | `KYC-4100` | At least one ID page must be provided |
| `error.id.pages.limit` | `KYC-4101` | No more than four ID pages may be provided |
| `error.id.page.required` | `KYC-4102` | ID page file must be provided |
| `error.id.page.size` | `KYC-4103` | ID page exceeds maximum size |
| `error.email.required` | `KYC-4200` | email must be provided |
| `error.email.invalid` | `KYC-4201` | email must be a valid email address |
| `error.address.required` | `KYC-4300` | address must be provided |
| `error.postalCode.required` | `KYC-4301` | postalCode must be provided |
| `error.postalCode.invalid` | `KYC-4302` | postalCode must contain exactly 10 digits |
| `error.englishInfo.firstName.required` | `KYC-4400` | firstNameEn must be provided |
| `error.englishInfo.lastName.required` | `KYC-4401` | lastNameEn must be provided |
| `error.englishInfo.telephone.required` | `KYC-4402` | telephone must be provided |
| `error.kyc.notStarted` | `KYC-4500` | KYC process has not been started |
| `error.kyc.status.queryFailed` | `KYC-4501` | Unable to query KYC status |
| `error.kyc.card.descriptorsRequired` | `KYC-4600` | Document descriptors must be provided |
| `error.kyc.id.descriptorsRequired` | `KYC-4601` | At least one ID page descriptor must be provided |
| `error.kyc.id.descriptorLimit` | `KYC-4602` | No more than four ID page descriptors are allowed |
| `error.kyc.id.descriptorNull` | `KYC-4603` | ID page descriptors must not be null |
| `error.kyc.selfie.descriptorRequired` | `KYC-4604` | Selfie descriptor must be provided |
| `error.kyc.signature.descriptorRequired` | `KYC-4605` | Signature descriptor must be provided |
| `error.kyc.video.descriptorRequired` | `KYC-4606` | Video descriptor must be provided |
| `error.kyc.consent.notAccepted` | `KYC-4607` | Consent must be accepted |
| `error.document.notFound` | `KYC-4700` | Document not found |
| `error.storage.descriptor.required` | `KYC-4800` | Document descriptor must be provided |
| `error.storage.documentType.required` | `KYC-4801` | documentType must be provided |
| `error.storage.descriptor.dataRequired` | `KYC-4802` | Document descriptor data must not be empty |
| `error.storage.unavailable` | `KYC-4803` | Object storage is unavailable. Please retry later. |

If you override the catalogue, keep the keys identical so clients can continue branching on `error.code`.

### Consent Acceptance

`POST /kyc/consent` receives a JSON body:

```json
{
  "processInstanceId": "<existing process instance>",
  "termsVersion": "v1.0",
  "accepted": true
}
```

- `accepted` **must** be `true`; any other value triggers `400 Bad Request`.
- The `processInstanceId` must exist in the read store; unknown IDs raise `404 Not Found`.
- On success the service persists the decision, publishes the `consent-accepted` Zeebe message, and returns `202 Accepted` with:
  - `processInstanceId`, `termsVersion`, `accepted`, `status=CONSENT_ACCEPTED`
- If the customer had already accepted the same terms, the response becomes `409 Conflict` with `status=CONSENT_ALREADY_ACCEPTED`.
- Other errors: `400` for validation issues or command rejections, `404` for missing processes, `500` for unexpected failures.

### Booklet Page Upload

> **Prerequisite for uploads:** All document-upload endpoints (`/kyc/booklets`, `/kyc/card`, `/kyc/selfie`, `/kyc/signature`, `/kyc/video`) require the referenced `processInstanceId` to exist. Requests referencing an unknown instance return `404 Not Found`.

`POST /kyc/booklets` accepts **1 to 4** multipart parts named `pages` plus a text part `processInstanceId`. Supported formats are JPEG, PNG, and PDF; each file must be ≤2 MB.

- Missing files, more than four pages, unsupported media types, or oversize uploads return `400 Bad Request`.
- The service stores the payload in MinIO, calls the external booklet-validation ESB, and persists both the descriptors and validation traces.
- Duplicate submissions yield `409 Conflict` with `status=ID_PAGES_ALREADY_UPLOADED`.
- Successful responses (`202 Accepted`) contain:
  - `processInstanceId`, `pageCount`, `pageSizes`
  - `validationResults` (trackId/type/rotation of each page)
  - `status=ID_PAGES_RECEIVED`
- Additional failures follow the shared pattern: `404` for missing processes, `500` for unexpected errors.

### Card Document Upload

`POST /kyc/card` accepts three multipart fields: `frontImage`, `backImage`, and `processInstanceId`. Each image must be an allowed image type and ≤2 MB.

- Empty files, unsupported content types, or oversize uploads return `400 Bad Request`.
- The process instance is validated before OCR extraction; unknown IDs cause `404 Not Found`.
- The service extracts OCR (front/back) via the ESB, updates the customer entity, stores files in MinIO, and publishes `card-documents-uploaded`.
- Duplicate uploads respond with `409 Conflict` and `status=CARD_DOCUMENTS_ALREADY_UPLOADED`.
- Successful uploads (`202 Accepted`) return `processInstanceId`, `frontImageSize`, `backImageSize`, and `status=CARD_DOCUMENTS_RECEIVED`.
- Additional errors: `400` for mismatched national codes or unreadable files, `404` for missing processes, and `500` for unexpected exceptions.

### Selfie Upload

`POST /kyc/selfie` accepts a `selfie` part (≤2 MB) plus `processInstanceId`.

- After validating the payload, the service invokes the ESB face-detection API. Confidence values below `0.9` raise `400 Bad Request` with key `error.workflow.selfieValidation.failed`.
- Duplicate submissions return `409 Conflict` with `status=SELFIE_ALREADY_UPLOADED`.
- Successful responses (`202 Accepted`) include:
  - `processInstanceId`, `selfieSize`
  - `faceConfidence` and `faceTrackId` from the ESB
  - `status=SELFIE_RECEIVED`
- Other failures follow the shared pattern (`404` for unknown processes, `500` for unexpected errors).

### Signature Upload

`POST /kyc/signature` receives an image file (≤2 MB) and `processInstanceId`.

- Empty/oversize files return `400`; unknown processes result in `404`.
- Duplicate uploads lead to `409 Conflict` with `status=SIGNATURE_ALREADY_UPLOADED`.
- Successful responses (`202 Accepted`) carry `processInstanceId`, `signatureSize`, and `status=SIGNATURE_RECEIVED`.

### Video Upload

`POST /kyc/video` requires three parts: `video` (≤10 MB), still frame `image1` (≤5 MB), and `processInstanceId`.

- Both files must match the allowed content types; violations raise `400 Bad Request`.
- Duplicate uploads return `409 Conflict` with `status=VIDEO_ALREADY_UPLOADED`.
- After storing the payload, the service asks the ESB liveness API to evaluate the clip and publishes `video-uploaded`. The `202 Accepted` response includes:
  - `processInstanceId`
  - `videoSize`
  - `match` (true when `livenessScore ≥ 0.8`)
  - `livenessScore`, `isReal`, `trackId`, `framesCount`
  - `status=VIDEO_RECEIVED`
- Additional failures: `404` for unknown processes, `500` for liveness/processing issues.

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
