

1. **OTP Infrastructure**

    * What service will generate and deliver OTPs (SMS, email, authenticator app)?
    * Do we operate our own OTP server, or integrate with a trusted third-party?
    * How do we validate OTPs securely (expiry, replay protection, fraud resistance)?
A: we should implement local OTP
2. **OCR (Optical Character Recognition)**

    * Which OCR technology will we adopt for ID cards, booklets, and utility bills? (e.g., Tesseract, PaddleOCR, Google Vision, or local Persian-optimized models).
    * Do we need language-specific OCR (Persian/English bilingual)?
    * Will OCR run **on-premise** (compliance-sensitive) or in the **cloud** (scalability)?
A: we don't have OCR now, and we use third party(Dibarayan) but in future should have this feature.
3. **Video Liveness Detection**

    * How will we ensure the video is from a live person (blink detection, head movements, challenge–response prompts)?
    * Should we rely on AI/ML-based liveness detection or integrate with a certified vendor?
    * How do we handle **deepfake detection** for fraud prevention?
A: we don't have OCR now, and we use third party(Dibarayan) but in future should have this feature.

4. **National Services Integration**

    * How will we call Shahkar (SIM ↔ National Code verification), National Registry (ثبت احوال), and Post (address validation)?
    * What are the request/response object formats for each integration?
    * Do we need retry, caching, and failover strategies for when government services are offline or slow?
A: These services handled in user services in another microservice.
5. **Digital Signature & Cryptographic Keys**

    * Should we generate **asymmetric key pairs (RSA/ECDSA)** for each customer at onboarding?
    * How do we securely store private keys (HSM, software vault, SIM card)?
    * Do we need signatures to be **legally recognized by CBI’s نماد PKI infrastructure**?
A: we don't have any plan, but we should have one and its necessary for the project.
6. **Fraud Detection on Documents**

    * What techniques should we use for document fraud checks (image forensics, AI-based forgery detection, hash comparison against registry)?
    * Do we need to integrate with external fraud intelligence databases?
    * How do we flag suspicious patterns (same photo used by multiple identities)?
A: we don't have OCR now, and we use third party(Dibarayan) but in future should have this feature.

7. **Process Orchestration**

    * Should we manage the KYC flow using a **BPMN engine like Camunda 8**, so steps are auditable and adaptable?
    * Which tasks should be **human-in-the-loop** (manual approval) vs. fully automated?
    * How do we ensure resilience and recovery in long-running KYC processes?
A: All the kyc flow handled by Camunda.
8. **Architecture Style**

    * Should we adopt **CQRS (Command–Query Responsibility Segregation)** for KYC to separate high-write operations (uploads, validations) from read queries (KYC status, audit trails)?
    * Would Event Sourcing add auditability for regulatory requirements?
    * Or is a simpler CRUD model sufficient?
A: It is necessary to use CQRS model in development.
9. **Document Storage Strategy**

    * Should upload documents be stored directly in the **database (BLOBs)** for integrity, or in **object storage (MinIO/S3)** for scalability?
    * How do we guarantee **hash-based tamper detection** no matter where files live?
    * What retention policies must we follow (per CBI / AML / GDPR regulations)?
A: all the files are stored in paths, and they have no hash or encryption.
10. **Object Storage Solution (MinIO / Alternatives)**

    * Should we adopt **MinIO** as the S3-compatible backend for document storage?
    * How do we configure redundancy, encryption at rest, and access control for sensitive KYC docs?
    * Do we need future-proofing so we can migrate easily to the national cloud(درگاه ملی دولت)?
A: we use MinaIO, and we have another microservice to use MinaIO.
---

S3 = Simple Storage Service — originally from Amazon (AWS), but now it’s more like a universal protocol for object storage.
