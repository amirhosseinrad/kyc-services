package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "KYC_STEP_STATUS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "process")
public class StepStatus {

    @Id
    @GeneratedValue
    private Long id;

    private String stepName;

    @Enumerated(EnumType.STRING)
    private State state;

    private LocalDateTime timestamp;

    @ManyToOne
    private ProcessInstance process;

    public enum State {
        STARTED,
        PASSED,
        FAILED
    }
}
