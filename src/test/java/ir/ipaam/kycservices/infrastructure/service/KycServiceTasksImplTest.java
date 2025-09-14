package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.infrastructure.service.impl.KycServiceTasksImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KycServiceTasksImplTest {

    private final KycServiceTasks tasks = new KycServiceTasksImpl();

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
        assertEquals("UNKNOWN", tasks.checkKycStatus("0024683416"));
    }
}

