package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kyc_status_view")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusView {
    @Id
    private String nationalCode;
    private String status;
    private String camundaInstanceId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @ManyToOne
    private Customer customer;
}
