package ir.ipaam.kycservices.infrastructure.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Document {
    @Id
    @GeneratedValue
    private Long id;

    private String type;       // CARD, ID_BOOKLET, PHOTO, VIDEO, SIGNATURE
    private String storagePath;
    private String hash;
    private boolean verified;

    @ManyToOne
    private KycProcessInstance process;
}
