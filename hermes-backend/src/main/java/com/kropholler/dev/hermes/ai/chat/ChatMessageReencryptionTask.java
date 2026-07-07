package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.crypto.Reencryptable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class ChatMessageReencryptionTask implements Reencryptable {

    private static final int BATCH_SIZE = 500;

    private final ChatMessageRepository chatMessageRepository;
    private final FieldEncryptor fieldEncryptor;

    ChatMessageReencryptionTask(ChatMessageRepository chatMessageRepository, FieldEncryptor fieldEncryptor) {
        this.chatMessageRepository = chatMessageRepository;
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public String tableName() {
        return "chat_messages";
    }

    @Override
    @Transactional
    public int reencryptBatch() {
        int version = fieldEncryptor.getCurrentVersion();
        List<ChatMessageEntity> stale = chatMessageRepository
            .findByEncryptionKeyVersionLessThan(version, PageRequest.of(0, BATCH_SIZE));
        for (ChatMessageEntity m : stale) {
            chatMessageRepository.reencrypt(m.getId(), m.getContent(), version);
        }
        return stale.size();
    }
}
