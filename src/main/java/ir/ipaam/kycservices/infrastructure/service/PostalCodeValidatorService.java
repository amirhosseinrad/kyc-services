package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.infrastructure.service.dto.PostalCodeRange;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PostalCodeValidatorService {

    private static final Pattern TEN_DIGIT_PATTERN = Pattern.compile("^\\d{10}$");
    private List<PostalCodeRange> ranges;

    @PostConstruct
    public void init() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(
                                getClass().getClassLoader()
                                        .getResourceAsStream("postalcode.csv")
                        ),
                        StandardCharsets.UTF_8))) {

            // Skip header, parse CSV
            ranges = reader.lines().skip(1)
                    .map(line -> line.split(","))
                    .filter(cols -> cols.length >= 4)
                    .map(cols -> new PostalCodeRange(
                            cols[0].trim(),
                            cols[1].trim(),
                            Integer.parseInt(cols[2].trim()),
                            Integer.parseInt(cols[3].trim())
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load postal code ranges", e);
        }
    }

    public Optional<Map<String, String>> validateAndEnrich(String postalCode) {
        if (postalCode == null) return Optional.empty();
        String normalized = postalCode.replace("-", "").trim();

        // Step 1: Format validation
        if (!TEN_DIGIT_PATTERN.matcher(normalized).matches()) return Optional.empty();

        // Step 2: Extract first 5 digits as integer
        int first5 = Integer.parseInt(normalized.substring(0, 5));

        // Step 3: Lookup in ranges
        return ranges.stream()
                .filter(r -> r.contains(first5))
                .findFirst()
                .map(r -> Map.of(
                        "province", r.getProvince(),
                        "city", r.getCity(),
                        "normalizedPostalCode", normalized
                ));
    }
}
