package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "KYC_PROCESS_INSTANCE")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ProcessInstance {
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
    private List<StepStatus> statuses = new ArrayList<>();

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL)
    @ToString.Exclude
    private List<Address> addresses = new ArrayList<>();
}
