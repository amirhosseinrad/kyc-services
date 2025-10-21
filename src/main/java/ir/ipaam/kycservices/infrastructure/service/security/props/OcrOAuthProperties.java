package ir.ipaam.kycservices.infrastructure.service.security.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ocr.card.oauth")
public class OcrOAuthProperties {

    private String tokenUrl;
    private String clientId;
    private String clientSecret;
    private String username;
    private String password;
    private String scope;
    private String grantType = "password";
}
