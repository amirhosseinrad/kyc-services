package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "KYC_USER_TASK_DATA")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class UserTaskData {
    @Id
    @GeneratedValue
    private Long id;

    private String taskType; // CONSENT, DOCUMENT_UPLOAD, ADDRESS, SIGNATURE
    private String payload;  // JSON form submission
    private LocalDateTime submittedAt;

    @ManyToOne
    private ProcessInstance process;
}
