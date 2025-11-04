package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.AddressVerificationRequest;
import ir.ipaam.kycservices.application.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc")
@Tag(name = "Address Verification", description = "Collect and validate postal address information for a KYC process.")
public class AddressController {

    private final AddressService addressService;

    @Operation(
            summary = "Collect postal address details",
            description = "Receives the customer's address and postal code for the active KYC process, publishes the "
                    + "address-and-zipcode-collected workflow message, and when validation succeeds publishes the "
                    + "zipcode-and-address-validated message."
    )
    @PostMapping(path = "/address")
    public ResponseEntity<Map<String, Object>> collectAddress(
            @RequestBody AddressVerificationRequest request,
            @RequestHeader(value = "stage", required = false) String stageHeader) {
        return addressService.collectAddress(request, stageHeader);
    }
}

