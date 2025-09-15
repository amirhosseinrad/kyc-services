package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class KycProcessInstance {
    @Id
    @GeneratedValue
    private Long id;

    private String camundaInstanceId;
    private String status; // ACTIVE, COMPLETED, REJECTED
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @ManyToOne
    private Customer customer;

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL)
    @ToString.Exclude
    private List<KycStepStatus> statuses = new ArrayList<>();
}
