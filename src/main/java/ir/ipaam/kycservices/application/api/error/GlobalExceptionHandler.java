package ir.ipaam.kycservices.application.api.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandExecutionException;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.*;

@Slf4j
@RestControllerAdvice
@Import({LocalizedErrorMessageService.class, ErrorMessageConfig.class})
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final LocalizedErrorMessageService localizedErrorMessageService;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), VALIDATION_FAILED);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Failed to parse request body", ex);
        return buildResponse(HttpStatus.BAD_REQUEST, REQUEST_BODY_INVALID, REQUEST_BODY_INVALID);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(FieldError::getField,
                        LinkedHashMap::new,
                        Collectors.mapping(DefaultMessageSourceResolvable::getDefaultMessage, Collectors.toList())));

        List<String> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .toList();

        Map<String, Object> details = new LinkedHashMap<>();
        if (!fieldErrors.isEmpty()) {
            details.put("fieldErrors", fieldErrors);
        }
        if (!globalErrors.isEmpty()) {
            details.put("globalErrors", globalErrors);
        }

        String messageKey = findFirstResolvableMessageKey(Stream.concat(
                        fieldErrors.values().stream().flatMap(List::stream),
                        globalErrors.stream()))
                .orElse(VALIDATION_FAILED);

        return buildResponse(HttpStatus.BAD_REQUEST, messageKey, VALIDATION_FAILED,
                details.isEmpty() ? null : details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, List<String>> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(violation -> violation.getPropertyPath().toString(),
                        LinkedHashMap::new,
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())));
        Map<String, Object> details = violations.isEmpty() ? null : Map.of("violations", violations);
        String messageKey = findFirstResolvableMessageKey(violations.values().stream().flatMap(List::stream))
                .orElse(VALIDATION_FAILED);
        return buildResponse(HttpStatus.BAD_REQUEST, messageKey, VALIDATION_FAILED, details);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), PROCESS_NOT_FOUND);
    }

    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ErrorResponse> handleFileProcessingException(FileProcessingException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), FILE_READ_FAILURE);
    }

    @ExceptionHandler(ObjectStorageUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleObjectStorageUnavailableException(ObjectStorageUnavailableException ex) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), STORAGE_UNAVAILABLE);
    }

    @ExceptionHandler(CommandExecutionException.class)
    public ResponseEntity<ErrorResponse> handleCommandExecutionException(CommandExecutionException ex) {
        Throwable rootCause = resolveRootCause(ex);
        if (rootCause instanceof IllegalArgumentException illegalArgumentException) {
            return handleIllegalArgumentException(illegalArgumentException);
        }
        if (rootCause instanceof ResourceNotFoundException resourceNotFoundException) {
            return handleResourceNotFoundException(resourceNotFoundException);
        }
        if (rootCause instanceof ConstraintViolationException constraintViolationException) {
            return handleConstraintViolationException(constraintViolationException);
        }
        if (rootCause instanceof FileProcessingException fileProcessingException) {
            return handleFileProcessingException(fileProcessingException);
        }
        String messageKey = Optional.ofNullable(rootCause)
                .map(Throwable::getMessage)
                .filter(m -> !m.isBlank())
                .or(() -> Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()))
                .orElse(null);
        log.warn("Command execution rejected: {}", messageKey, ex);
        return buildResponse(HttpStatus.CONFLICT, messageKey, COMMAND_EXECUTION_FAILED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        String messageKey = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse(null);
        log.error("Unexpected error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, messageKey, UNEXPECTED_ERROR);
    }

    private Throwable resolveRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && cause.getCause() != null && cause != cause.getCause()) {
            cause = cause.getCause();
        }
        return cause == null ? throwable : cause;
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String messageKey, String fallbackKey) {
        return buildResponse(status, messageKey, fallbackKey, null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status,
                                                        String messageKey,
                                                        String fallbackKey,
                                                        Map<String, ?> details) {
        LocalizedMessage message = localizedErrorMessageService.resolve(messageKey, fallbackKey);
        return ResponseEntity.status(status).body(ErrorResponse.of(message, details));
    }

    private Optional<String> findFirstResolvableMessageKey(Stream<String> candidates) {
        return candidates
                .filter(StringUtils::hasText)
                .filter(messageKey -> localizedErrorMessageService.findMessage(messageKey).isPresent())
                .findFirst();
    }
}
