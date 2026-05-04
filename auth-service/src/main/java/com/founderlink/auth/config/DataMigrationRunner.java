package com.founderlink.auth.config;

import com.founderlink.auth.entity.AuthProvider;
import com.founderlink.auth.entity.User;
import com.founderlink.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time data migration that runs on startup.
 *
 * Sets {@code email_verified = true} for all users that registered
 * before the email-verification feature was introduced (i.e. all
 * LOCAL users whose {@code email_verified} is currently false).
 *
 * This prevents existing users from being locked out after the new
 * login guard is applied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataMigrationRunner implements ApplicationRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> unverified = userRepository.findAll().stream()
                .filter(u -> !u.isEmailVerified() && u.getAuthProvider() == AuthProvider.LOCAL)
                .toList();

        if (unverified.isEmpty()) {
            log.debug("DataMigration: all existing LOCAL users are already verified — nothing to do.");
            return;
        }

        unverified.forEach(u -> u.setEmailVerified(true));
        userRepository.saveAll(unverified);
        log.info("DataMigration: marked {} existing LOCAL user(s) as email-verified.", unverified.size());
    }
}
