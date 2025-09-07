package ir.ipaam.kycservices.infrastructure.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class AddressVerification {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private KycProcessInstance process;

    private String address;
    private String zipCode;
    private boolean zipValid;
}
