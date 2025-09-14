package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

@Entity
public class Consent {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private KycProcessInstance process;

    private boolean accepted;
    private LocalDateTime acceptedAt;
    private String termsVersion;
}
