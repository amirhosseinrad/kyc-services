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

### **Dynamic Deployment**
Supports runtime BPMN deployment through a REST API, with **hash-based deduplication** and **Zeebe deployment metadata** (deployment key, process version) stored in the database.

### **Event-Driven Architecture**
**Axon Framework** powers **CQRS & Event Sourcing** for process state tracking and auditing.

### **Status API**
Exposes endpoints to query and update the KYC process state from external services.

### **Integration-Ready**
Designed for integration with OCR, fraud detection, and customer information services.

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

| Method | Endpoint             | Description                                      |
|-------|-----------------------|--------------------------------------------------|
| POST  | `/kyc/process`        | Start a new KYC process instance                 |
| POST  | `/kyc/status`         | Get current KYC process status                   |
| POST  | `/kyc/consent`        | Record the customer's consent for the KYC terms |
| POST  | `/kyc/selfie`         | Upload a selfie image for biometric checks       |
| POST  | `/kyc/video`          | Upload a recorded customer video                 |
| POST  | `/kyc/documents/id`   | Upload 1–4 ID document pages                     |
| POST  | `/kyc/documents/card` | Upload front/back images of the national card    |
| POST  | `/bpmn/deploy`        | Deploy a BPMN file (multipart upload)            |

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

### ID Page Upload

> **Prerequisite for uploads:** All document-upload endpoints (`/kyc/documents/id`, `/kyc/documents/card`, `/kyc/selfie`, `/kyc/video`) require the referenced `processInstanceId` to exist. Requests referencing an unknown instance return `404 Not Found`.

`POST /kyc/documents/id` accepts up to **four multipart files** named `pages` plus a `processInstanceId` field. Each file must contain the binary payload for a booklet page (≤2 MB).

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
