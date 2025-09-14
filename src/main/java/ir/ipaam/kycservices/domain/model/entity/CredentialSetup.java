package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class CredentialSetup {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private KycProcessInstance process;

    private String passwordHash;
    private String fingerprintHash;
}
