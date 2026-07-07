package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.crypto.Reencryptable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class NotificationReencryptionTask implements Reencryptable {

    private static final int BATCH_SIZE = 500;

    private final NotificationRepository notificationRepository;
    private final FieldEncryptor fieldEncryptor;

    NotificationReencryptionTask(NotificationRepository notificationRepository, FieldEncryptor fieldEncryptor) {
        this.notificationRepository = notificationRepository;
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public String tableName() {
        return "notifications";
    }

    @Override
    @Transactional
    public int reencryptBatch() {
        int version = fieldEncryptor.getCurrentVersion();
        List<NotificationEntity> stale = notificationRepository
            .findByEncryptionKeyVersionLessThan(version, PageRequest.of(0, BATCH_SIZE));
        for (NotificationEntity n : stale) {
            notificationRepository.reencrypt(n.getId(), n.getTitle(), n.getBody(), version);
        }
        return stale.size();
    }
}
