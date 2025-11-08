package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.service.SignatureService;
import ir.ipaam.kycservices.application.service.dto.SignatureUploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc")
@Tag(name = "Signature Service", description = "Collect handwritten signatures for contract fulfillment.")
public class SignatureController {

    private final SignatureService signatureService;

    @Operation(
            summary = "Upload a handwritten signature",
            description = "Accepts a scanned signature image for the active process, stores it, and emits a "
                    + "signature-uploaded workflow message. Rejects missing or oversized files."
    )
    @PostMapping(path = "/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSignature(
            @RequestPart("signature") MultipartFile signature,
            @RequestPart("processInstanceId") String processInstanceId) {
        SignatureUploadResult result = signatureService.uploadSignature(signature, processInstanceId);
        return ResponseEntity.status(result.status()).body(result.body());
    }
}
