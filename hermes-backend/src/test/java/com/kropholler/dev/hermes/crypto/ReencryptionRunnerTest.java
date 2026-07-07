package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReencryptionRunnerTest {

    @Mock Reencryptable chatMessages;
    @Mock Reencryptable notifications;

    @Test
    void run_loopsEachReencryptableUntilItReturnsZero() throws Exception {
        when(chatMessages.tableName()).thenReturn("chat_messages");
        when(chatMessages.reencryptBatch()).thenReturn(2, 1, 0);
        when(notifications.tableName()).thenReturn("notifications");
        when(notifications.reencryptBatch()).thenReturn(0);
        ReencryptionRunner runner = new ReencryptionRunner(List.of(chatMessages, notifications));

        runner.run();

        verify(chatMessages, org.mockito.Mockito.times(3)).reencryptBatch();
        verify(notifications, org.mockito.Mockito.times(1)).reencryptBatch();
    }
}
