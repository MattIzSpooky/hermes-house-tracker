package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.crypto.Reencryptable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class AgentTaskReencryptionTask implements Reencryptable {

    private static final int BATCH_SIZE = 500;

    private final AgentTaskRepository agentTaskRepository;
    private final FieldEncryptor fieldEncryptor;

    AgentTaskReencryptionTask(AgentTaskRepository agentTaskRepository, FieldEncryptor fieldEncryptor) {
        this.agentTaskRepository = agentTaskRepository;
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public String tableName() {
        return "agent_tasks";
    }

    @Override
    @Transactional
    public int reencryptBatch() {
        int version = fieldEncryptor.getCurrentVersion();
        List<AgentTaskEntity> stale = agentTaskRepository
            .findByEncryptionKeyVersionLessThan(version, PageRequest.of(0, BATCH_SIZE));
        for (AgentTaskEntity t : stale) {
            agentTaskRepository.reencrypt(t.getId(), t.getName(), t.getPayload(), version);
        }
        return stale.size();
    }
}
