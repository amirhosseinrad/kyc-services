package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "KYC_OCR_RESULT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OcrResult {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Document document;

    private String extractedText;
    private boolean matchedWithProvidedData;
    private double confidenceScore;
}
