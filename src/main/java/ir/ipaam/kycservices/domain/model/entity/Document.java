package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "KYC_DOCUMENT")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Document {
    @Id
    @GeneratedValue
    private Long id;

    private String type;       // CARD, ID_BOOKLET, PHOTO, VIDEO, SIGNATURE
    private String storagePath;
    private String hash;
    private String inquiryDocumentId;
    private boolean verified;
    private boolean encrypted;
    private String encryptionIv;

    @ManyToOne
    private ProcessInstance process;
}
