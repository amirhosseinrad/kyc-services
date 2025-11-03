package ir.ipaam.kycservices.application.api.error;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ErrorMessageConfig {

    private static final Logger log = LoggerFactory.getLogger(ErrorMessageConfig.class);

    private final Map<String, LocalizedMessage> messages;

    public ErrorMessageConfig(
            @Value("${error.messages.location:classpath:error-messages.json}") String location,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        this.messages = loadMessages(location, objectMapper, resourceLoader);
    }

    public Map<String, LocalizedMessage> getMessages() {
        return messages;
    }

    private Map<String, LocalizedMessage> loadMessages(String location, ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("Error message resource {} not found; falling back to empty catalogue", location);
            return Collections.emptyMap();
        }

        try (InputStream is = resource.getInputStream()) {
            Map<String, LocalizedMessage> loaded = objectMapper.readValue(is, new TypeReference<>() {
            });
            if (CollectionUtils.isEmpty(loaded)) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load error messages from " + location, e);
        }
    }
}
