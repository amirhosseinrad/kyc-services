package ir.ipaam.kycservices.application.workflow;

public class WorkflowTaskException extends RuntimeException {

    public WorkflowTaskException(String messageKey) {
        super(messageKey);
    }

    public WorkflowTaskException(String messageKey, Throwable cause) {
        super(messageKey, cause);
    }
}
