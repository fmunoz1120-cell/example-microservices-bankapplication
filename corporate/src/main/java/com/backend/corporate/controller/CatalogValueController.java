package com.backend.corporate.controller;

import com.backend.corporate.dto.*;
import com.backend.corporate.service.CorporateDataService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog-values")
public class CatalogValueController {
  private final CorporateDataService service;

  public CatalogValueController(CorporateDataService service) {
    this.service = service;
  }

  @GetMapping
  public Page<CatalogValueView> list(
      @RequestParam(required = false) Long catalogTypeId, Pageable pageable) {
    return service.listValues(catalogTypeId, pageable);
  }

  @GetMapping("/{id}")
  public CatalogValueView get(@PathVariable Long id) {
    return service.getValue(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CatalogValueView create(@Valid @RequestBody CatalogValueRequest request) {
    return service.saveValue(null, request);
  }

  @PutMapping("/{id}")
  public CatalogValueView update(
      @PathVariable Long id, @Valid @RequestBody CatalogValueRequest request) {
    return service.saveValue(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.deleteValue(id);
  }
}
