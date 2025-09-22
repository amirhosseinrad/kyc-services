package ir.ipaam.kycservices.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class EnglishPersonalInfoProvidedEvent {
    private String processInstanceId;
    private String nationalCode;
    private String firstNameEn;
    private String lastNameEn;
    private String email;
    private String telephone;
    private LocalDateTime providedAt;

}



