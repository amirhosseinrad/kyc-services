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

| Method | Endpoint             | Description                       |
|-------|---------------------|-----------------------------------|
| POST | `/kyc/process`       | Start a new KYC process instance |
| POST | `/kyc/status`        | Get current KYC process status   |
| POST | `/bpmn/deploy`       | Deploy a BPMN file (multipart upload) |

---
