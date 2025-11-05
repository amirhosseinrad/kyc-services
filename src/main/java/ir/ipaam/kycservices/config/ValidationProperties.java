package ir.ipaam.kycservices.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kyc.validation")
@Getter
@Setter
public class ValidationProperties {

    /**
     * Controls whether Iranian national code format validation is enforced.
     */
    private boolean nationalCodeEnabled = true;
}
