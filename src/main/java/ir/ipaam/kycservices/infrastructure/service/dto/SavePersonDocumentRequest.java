package ir.ipaam.kycservices.infrastructure.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavePersonDocumentRequest {

    @JsonProperty("tokenValue")
    private String tokenValue;

    @JsonProperty("documentType")
    private int documentType;

    @JsonProperty("fileData")
    private FileData fileData;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileData {

        @JsonProperty("name")
        private String name;

        @JsonProperty("fileName")
        private String fileName;

        @JsonProperty("content")
        private String content;
    }
}
