package com.atp.platform.repository;

import com.atp.platform.entity.SecureCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecureCredentialRepository extends JpaRepository<SecureCredential, Long> {
    List<SecureCredential> findAllByOrderByUpdatedAtDesc();
}
