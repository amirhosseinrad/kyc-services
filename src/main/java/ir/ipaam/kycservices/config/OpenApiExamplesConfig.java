package ir.ipaam.kycservices.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Configuration
public class OpenApiExamplesConfig {

    private final Random random = new SecureRandom();

    @Bean
    public OpenApiCustomizer dynamicNationalCodeExampleCustomiser() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            PathItem pathItem = openApi.getPaths().get("/kyc/start");
            if (pathItem == null) {
                return;
            }

            Operation postOperation = pathItem.getPost();
            if (postOperation == null || postOperation.getRequestBody() == null) {
                return;
            }

            Content content = postOperation.getRequestBody().getContent();
            if (content == null) {
                return;
            }

            MediaType mediaType = content.get("application/json");
            if (mediaType == null) {
                return;
            }

            Map<String, Object> example = new HashMap<>();
            example.put("nationalCode", generateNationalCode());
            mediaType.setExample(example);
        };
    }

    private String generateNationalCode() {
        while (true) {
            int[] digits = new int[9];
            boolean allSame = true;
            for (int i = 0; i < digits.length; i++) {
                digits[i] = random.nextInt(10);
                if (i > 0 && digits[i] != digits[0]) {
                    allSame = false;
                }
            }

            if (allSame) {
                continue;
            }

            int sum = 0;
            for (int i = 0; i < digits.length; i++) {
                sum += digits[i] * (10 - i);
            }

            int remainder = sum % 11;
            int checkDigit = remainder < 2 ? remainder : 11 - remainder;

            StringBuilder builder = new StringBuilder(10);
            for (int digit : digits) {
                builder.append(digit);
            }
            builder.append(checkDigit);
            return builder.toString();
        }
    }
}
