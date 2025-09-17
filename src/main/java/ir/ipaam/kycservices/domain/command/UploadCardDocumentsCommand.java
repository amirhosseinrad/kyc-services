package ir.ipaam.kycservices.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadCardDocumentsCommand {

    private final String processInstanceId;
    private final byte[] frontImage;
    private final byte[] backImage;
    private final String frontImageName;
    private final String backImageName;
    private final String frontImageContentType;
    private final String backImageContentType;
}
