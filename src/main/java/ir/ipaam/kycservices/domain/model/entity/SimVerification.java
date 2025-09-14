package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class SimVerification {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private KycProcessInstance process;

    private String mobile;
    private boolean matchedWithNationalCode;
    private int retryCount;
    private String status; // SUCCESS, FAILED, TIMEOUT
}
