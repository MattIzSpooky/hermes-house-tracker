package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.crypto.Reencryptable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class UserProfileReencryptionTask implements Reencryptable {

    private static final int BATCH_SIZE = 500;

    private final UserProfileRepository userProfileRepository;
    private final FieldEncryptor fieldEncryptor;

    UserProfileReencryptionTask(UserProfileRepository userProfileRepository, FieldEncryptor fieldEncryptor) {
        this.userProfileRepository = userProfileRepository;
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public String tableName() {
        return "user_profiles";
    }

    @Override
    @Transactional
    public int reencryptBatch() {
        int version = fieldEncryptor.getCurrentVersion();
        List<UserProfileEntity> stale = userProfileRepository
            .findByEncryptionKeyVersionLessThan(version, PageRequest.of(0, BATCH_SIZE));
        for (UserProfileEntity p : stale) {
            userProfileRepository.reencrypt(p.getUserId(), p.getStreet(), p.getHouseNumber(),
                p.getHouseNumberAddition(), p.getZipCode(), p.getCity(), p.getProvince(), p.getEmail(),
                p.getLatitude(), p.getLongitude(), version);
        }
        return stale.size();
    }
}
