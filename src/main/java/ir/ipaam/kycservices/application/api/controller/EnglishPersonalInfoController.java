package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.EnglishPersonalInfoRequest;
import ir.ipaam.kycservices.application.api.service.EnglishPersonalInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/english-info")
@Validated
@Tag(name = "English Personal Info", description = "Capture Latin-script customer information required for downstream compliance.")
public class EnglishPersonalInfoController {

    private final EnglishPersonalInfoService englishPersonalInfoService;

    @Operation(
            summary = "Provide English personal details",
            description = "Validates and stores the applicant's English first name, last name, email, and telephone. "
                    + "Publishes a workflow update after the information is accepted."
    )
    @PostMapping
    public ResponseEntity<Map<String, Object>> provideEnglishPersonalInfo(@Valid @RequestBody EnglishPersonalInfoRequest request
    ) {
        return englishPersonalInfoService.provideEnglishPersonalInfo(request);
    }
}
