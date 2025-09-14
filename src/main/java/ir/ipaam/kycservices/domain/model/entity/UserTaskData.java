package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

@Entity
public class UserTaskData {
    @Id
    @GeneratedValue
    private Long id;

    private String taskType; // CONSENT, DOCUMENT_UPLOAD, ADDRESS, SIGNATURE
    private String payload;  // JSON form submission
    private LocalDateTime submittedAt;

    @ManyToOne
    private KycProcessInstance process;
}
