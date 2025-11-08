package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.CustomerInfoRequest;
import ir.ipaam.kycservices.application.service.CustomerInfoService;
import ir.ipaam.kycservices.application.service.dto.CustomerInfoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/customer-info")
@Validated
@Tag(name = "Customer Info", description = "Capture Latin-script customer information required for downstream compliance.")
public class CustomerInfoController {

    private final CustomerInfoService customerInfoService;

    @Operation(
            summary = "Provide English personal details",
            description = "Validates and stores the applicant's English first name, last name, email, and telephone. "
                    + "Publishes a workflow update after the information is accepted."
    )
    @PostMapping
    public ResponseEntity<CustomerInfoResponse> provideCustomerInfo(
            @Valid @RequestBody CustomerInfoRequest request) {
        CustomerInfoResponse response = customerInfoService.provideCustomerInfo(request);
        HttpStatus status = "CUSTOMER_INFO_ALREADY_PROVIDED".equals(response.status())
                ? HttpStatus.CONFLICT
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }
}
