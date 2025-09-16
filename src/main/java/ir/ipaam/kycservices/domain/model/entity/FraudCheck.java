package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "KYC_FRAUD_CHECK")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FraudCheck {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Document document;

    private String checkType; // IMAGE_TAMPERING, DEEPFAKE, DUPLICATE
    private boolean passed;
    private String details;
}
