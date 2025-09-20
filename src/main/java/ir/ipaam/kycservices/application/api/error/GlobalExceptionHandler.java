package ir.ipaam.kycservices.application.api.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandExecutionException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.context.annotation.Import;
import ir.ipaam.kycservices.config.ErrorMessageConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.*;

@Slf4j
@RestControllerAdvice
@Import({LocalizedErrorMessageService.class, ErrorMessageConfig.class})
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final LocalizedErrorMessageService localizedErrorMessageService;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, ex.getMessage(), VALIDATION_FAILED);
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

        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                VALIDATION_FAILED, VALIDATION_FAILED, details.isEmpty() ? null : details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, List<String>> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(violation -> violation.getPropertyPath().toString(),
                        LinkedHashMap::new,
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())));
        Map<String, Object> details = violations.isEmpty() ? null : Map.of("violations", violations);
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                VALIDATION_FAILED, VALIDATION_FAILED, details);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), PROCESS_NOT_FOUND);
    }

    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ErrorResponse> handleFileProcessingException(FileProcessingException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCode.FILE_PROCESSING_FAILED, ex.getMessage(), FILE_READ_FAILURE);
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
        return buildResponse(HttpStatus.CONFLICT, ErrorCode.COMMAND_REJECTED, messageKey, COMMAND_EXECUTION_FAILED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        String messageKey = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse(null);
        log.error("Unexpected error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UNEXPECTED_ERROR,
                messageKey, UNEXPECTED_ERROR);
    }

    private Throwable resolveRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && cause.getCause() != null && cause != cause.getCause()) {
            cause = cause.getCause();
        }
        return cause == null ? throwable : cause;
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, ErrorCode code, String messageKey, String fallbackKey) {
        return buildResponse(status, code, messageKey, fallbackKey, null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, ErrorCode code,
                                                        String messageKey, String fallbackKey,
                                                        Map<String, ?> details) {
        LocalizedMessage message = localizedErrorMessageService.resolve(messageKey, fallbackKey);
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message, details));
    }
}
