package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.application.service.CardOcrClient;
import ir.ipaam.kycservices.application.service.dto.CardOcrBackData;
import ir.ipaam.kycservices.application.service.dto.CardOcrFrontData;
import ir.ipaam.kycservices.infrastructure.service.dto.CardOcrBackResponse;
import ir.ipaam.kycservices.infrastructure.service.dto.CardOcrFrontResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardOcrClientImpl implements CardOcrClient {

    private static final String FRONT_SIDE = "front";
    private static final String BACK_SIDE = "back";
    private static final String ID_CARD_PART_NAME = "idcard";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    @Qualifier("cardOcrWebClient")
    private final WebClient cardOcrWebClient;

    @Override
    public CardOcrFrontData extractFront(byte[] content, String filename) {
        CardOcrFrontResponse response = cardOcrWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/kyc/v0.1/idcards/{side}/ocr").build(FRONT_SIDE))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(createMultipart(content, filename)))
                .retrieve()
                .bodyToMono(CardOcrFrontResponse.class)
                .block(DEFAULT_TIMEOUT);

        if (response == null || response.result() == null) {
            throw new IllegalStateException("Empty response from front OCR service");
        }
        return response.result().data();
    }

    @Override
    public CardOcrBackData extractBack(byte[] content, String filename) {
        CardOcrBackResponse response = cardOcrWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/kyc/v0.1/idcards/{side}/ocr").build(BACK_SIDE))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(createMultipart(content, filename)))
                .retrieve()
                .bodyToMono(CardOcrBackResponse.class)
                .block(DEFAULT_TIMEOUT);

        if (response == null || response.result() == null) {
            throw new IllegalStateException("Empty response from back OCR service");
        }
        return response.result().data();
    }

    private MultiValueMap<String, Object> createMultipart(byte[] content, String filename) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part(ID_CARD_PART_NAME, new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                if (StringUtils.hasText(filename)) {
                    return filename;
                }
                return ID_CARD_PART_NAME + ".jpg";
            }
        }).filename(StringUtils.hasText(filename) ? filename : ID_CARD_PART_NAME + ".jpg")
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        return builder.build();
    }
}
