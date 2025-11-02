package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.ValidateTrackingNumberRequest;
import ir.ipaam.kycservices.application.service.impl.BookletValidationServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/booklets")
@Tag(name = "Booklet Upload", description = "Submit multi-page scans of the customer's national ID booklet.")
public class BookletController {

    public static final long MAX_PAGE_SIZE_BYTES = BookletValidationServiceImpl.MAX_PAGE_SIZE_BYTES;

    private final BookletValidationServiceImpl bookletValidationServiceImpl;


    @PostMapping("/validate-tracking-national-card-number")
    public ResponseEntity<Map<String, Object>> validateTrackingNumber(
            @RequestBody ValidateTrackingNumberRequest request) {
        ResponseEntity<Map<String, Object>> response = bookletValidationServiceImpl.validateTrackingNumber(request);
        return response;
    }

    @Operation(
            summary = "Upload national ID booklet pages",
            description = "Accepts between one and four images representing the customer's national ID booklet. "
                    + "Validates file size, persists the payload, and notifies the KYC workflow of the upload."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadBookletPages(
            @RequestPart("pages") List<MultipartFile> pages,
            @RequestPart("processInstanceId") String processInstanceId) {
        return bookletValidationServiceImpl.uploadBookletPages(pages, processInstanceId);
    }
}
