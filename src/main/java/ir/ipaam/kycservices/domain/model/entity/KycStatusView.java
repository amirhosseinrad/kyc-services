package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kyc_status_view")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycStatusView {
    @Id
    private String nationalCode;
    private String status;
    private String processInstanceId;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;
}
