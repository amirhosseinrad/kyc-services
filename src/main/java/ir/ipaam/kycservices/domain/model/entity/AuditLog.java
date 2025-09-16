package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "KYC_AUDITLOG")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
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
