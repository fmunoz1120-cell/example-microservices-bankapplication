package com.backend.account.repository;

import com.backend.account.model.Transaction;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
  boolean existsByAccountId(Long accountId);

  Page<Transaction> findByAccountId(Long accountId, Pageable p);

  List<Transaction> findByAccountIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAscIdAsc(
      Long accountId, Instant start, Instant end);

  Optional<Transaction> findFirstByAccountIdAndDateLessThanOrderByDateDescIdDesc(
      Long accountId, Instant date);
}
