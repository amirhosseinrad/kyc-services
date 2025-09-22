package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.service.impl.KycServiceTasksImpl;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.KYC_STATUS_QUERY_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KycServiceTasksImplTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final KycServiceTasks tasks = new KycServiceTasksImpl(commandGateway, queryGateway);

    @Test
    void checkKycStatusReturnsProcessInstance() {
        ProcessInstance instance = new ProcessInstance();
        when(queryGateway.query(any(FindKycStatusQuery.class), eq(ResponseTypes.instanceOf(ProcessInstance.class))))
                .thenReturn(CompletableFuture.completedFuture(instance));
        assertEquals(instance, tasks.checkKycStatus("0024683416"));
    }

    @Test
    void checkKycStatusFailureThrowsIllegalStateException() {
        RuntimeException backendFailure = new RuntimeException("backend down");
        CompletableFuture<ProcessInstance> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(backendFailure);
        when(queryGateway.query(any(FindKycStatusQuery.class), eq(ResponseTypes.instanceOf(ProcessInstance.class))))
                .thenReturn(failedFuture);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> tasks.checkKycStatus("0024683416"));

        assertEquals(KYC_STATUS_QUERY_FAILED, exception.getMessage());
        assertEquals(backendFailure, exception.getCause());
    }
}

