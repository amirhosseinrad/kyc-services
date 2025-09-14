package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

@Entity
public class OtpVerification {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private KycProcessInstance process;

    private String mobile;
    private String otpCode;
    private LocalDateTime sentAt;
    private LocalDateTime verifiedAt;
    private boolean success;
}
