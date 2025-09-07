package ir.ipaam.kycservices.infrastructure.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
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
