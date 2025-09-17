package ir.ipaam.kycservices.infrastructure.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateTempTokenResponse {

    @JsonProperty("Result")
    private String result;

    @JsonProperty("RespnseCode")
    private Integer responseCode;

    @JsonProperty("RespnseMessage")
    private String responseMessage;

    @JsonProperty("Exception")
    private InquiryUploadResponseError exception;
}
