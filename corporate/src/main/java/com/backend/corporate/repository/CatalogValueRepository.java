package com.backend.corporate.repository;

import com.backend.corporate.model.CatalogValue;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogValueRepository extends JpaRepository<CatalogValue, Long> {
  Page<CatalogValue> findByCatalogTypeId(Long catalogTypeId, Pageable pageable);
}
