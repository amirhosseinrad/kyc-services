package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "process")
public class KycStepStatus {

    @Id
    @GeneratedValue
    private Long id;

    private String stepName;

    @Enumerated(EnumType.STRING)
    private State state;

    private LocalDateTime timestamp;

    @ManyToOne
    private KycProcessInstance process;

    public enum State {
        STARTED,
        PASSED,
        FAILED
    }
}
