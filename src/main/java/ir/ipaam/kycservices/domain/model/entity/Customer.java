package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "KYC_CUSTOMER")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Customer {
    @Id
    @GeneratedValue
    private Long id;
    @Column(unique = true, nullable = false)
    private String nationalCode;
    private String firstName;
    private String lastName;
    private String firstName_fa;
    private String lastName_fa;
    private String firstName_en;
    private String lastName_en;
    private LocalDate birthDate;
    private String mobile;
    private String email;
    private Boolean hasNewNationalCard;
    private String fatherName;
    private LocalDate cardExpirationDate;
    private String cardSerialNumber;
    private String cardBarcode;
    private String cardOcrFrontTrackId;
    private String cardOcrBackTrackId;
    private String NationalCardTrackingNumber;
}
