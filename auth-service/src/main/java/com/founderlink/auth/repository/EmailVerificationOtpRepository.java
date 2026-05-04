package com.founderlink.auth.repository;

import com.founderlink.auth.entity.EmailVerificationOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationOtpRepository extends JpaRepository<EmailVerificationOtp, Long> {

    Optional<EmailVerificationOtp> findByEmailAndOtp(String email, String otp);

    void deleteByEmail(String email);
}
