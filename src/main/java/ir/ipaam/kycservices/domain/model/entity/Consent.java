package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "KYC_CONSENT")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Consent {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private ProcessInstance process;

    private boolean accepted;
    private LocalDateTime acceptedAt;
    private String termsVersion;
}
