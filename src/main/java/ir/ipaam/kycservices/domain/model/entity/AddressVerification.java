package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "KYC_ADDRESS_VERIFICATION")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AddressVerification {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private ProcessInstance process;

    private String address;
    private String zipCode;
    private boolean zipValid;
}
