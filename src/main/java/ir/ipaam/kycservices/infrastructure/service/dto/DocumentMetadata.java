package ir.ipaam.kycservices.infrastructure.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentMetadata {
    private String path;
    private String hash;
}
