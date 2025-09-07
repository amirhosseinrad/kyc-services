package ir.ipaam.kycservices.infrastructure.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class AuditLog {
    @Id
    @GeneratedValue
    private Long id;

    private String camundaInstanceId;
    private String activityId; // BPMN task id
    private String action;     // "completed", "failed", "retried"
    private String actor;      // system / customer / compliance officer
    private LocalDateTime timestamp;
}
