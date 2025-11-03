package ir.ipaam.kycservices.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddressAndZipCodeCollectedEvent {
    private String processInstanceId;
    private String nationalCode;
    private String postalCode;
    private String address;
    private LocalDateTime collectedAt;
}

