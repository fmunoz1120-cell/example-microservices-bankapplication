package com.backend.account.repository;

import com.backend.account.model.ClientProjection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientProjectionRepository extends JpaRepository<ClientProjection, Long> {}
