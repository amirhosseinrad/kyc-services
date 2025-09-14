package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.service.impl.KycServiceTasksImpl;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KycServiceTasksImplTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final KycServiceTasks tasks = new KycServiceTasksImpl(commandGateway, queryGateway);

    @Test
    void validNationalCodePassesChecksum() {
        assertDoesNotThrow(() -> tasks.validateNationalCodeChecksum("0024683416", "proc1"));
    }

    @Test
    void invalidChecksumThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> tasks.validateNationalCodeChecksum("0024683415", "proc2"));
    }

    @Test
    void repeatedDigitsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> tasks.validateNationalCodeChecksum("1111111111", "proc3"));
    }

    @Test
    void wrongLengthRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> tasks.validateNationalCodeChecksum("123456789", "proc4"));
    }

    @Test
    void checkKycStatusDefaultsToUnknown() {
        when(queryGateway.query(any(FindKycStatusQuery.class), eq(ResponseTypes.instanceOf(String.class))))
                .thenReturn(CompletableFuture.completedFuture("UNKNOWN"));
        assertEquals("UNKNOWN", tasks.checkKycStatus("0024683416"));
    }
}

