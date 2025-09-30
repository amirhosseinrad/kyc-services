package ir.ipaam.kycservices.infrastructure.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PostalCodeRange {
    private String province;
    private String city;
    private int start;
    private int end;

    public boolean contains(int code) {
        return code >= start && code <= end;
    }

}
