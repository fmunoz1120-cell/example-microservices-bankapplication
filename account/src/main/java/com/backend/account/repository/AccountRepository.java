package com.backend.account.repository;

import com.backend.account.model.Account;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AccountRepository extends JpaRepository<Account, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id=:id")
  Optional<Account> lock(Long id);

  List<Account> findByClientIdOrderById(Long clientId);
}
