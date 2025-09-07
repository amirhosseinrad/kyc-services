package ir.ipaam.kycservices.infrastructure.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
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
