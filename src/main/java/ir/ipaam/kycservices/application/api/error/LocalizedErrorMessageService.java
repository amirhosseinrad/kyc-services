package ir.ipaam.kycservices.application.api.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Service
public class LocalizedErrorMessageService {

    private static final Logger log = LoggerFactory.getLogger(LocalizedErrorMessageService.class);

    private final Map<String, LocalizedMessage> messages;

    public LocalizedErrorMessageService(ErrorMessageConfig config) {
        this.messages = config.getMessages();
    }

    public LocalizedMessage resolve(String key) {
        return resolve(key, ErrorMessageKeys.UNEXPECTED_ERROR);
    }

    public LocalizedMessage resolve(String key, String fallbackKey) {
        Optional<LocalizedMessage> directMatch = lookup(key);
        if (directMatch.isPresent()) {
            return directMatch.get();
        }

        Optional<LocalizedMessage> fallbackMatch = lookup(fallbackKey);
        if (fallbackMatch.isPresent()) {
            return fallbackMatch.get();
        }

        String candidate = StringUtils.hasText(key) ? key : fallbackKey;
        if (StringUtils.hasText(candidate)) {
            log.debug("Missing localized message for key '{}'; using key as fallback text", candidate);
            return new LocalizedMessage(candidate, candidate);
        }
        return new LocalizedMessage("Unexpected error", "خطای غیرمنتظره");
    }

    public Optional<LocalizedMessage> findMessage(String key) {
        return lookup(key);
    }

    private Optional<LocalizedMessage> lookup(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        LocalizedMessage message = messages.get(key);
        return Optional.ofNullable(message);
    }
}
