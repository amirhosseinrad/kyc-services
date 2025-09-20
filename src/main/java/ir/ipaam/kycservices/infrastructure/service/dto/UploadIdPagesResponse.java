package ir.ipaam.kycservices.infrastructure.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadIdPagesResponse {
    private List<DocumentMetadata> pages;
}
