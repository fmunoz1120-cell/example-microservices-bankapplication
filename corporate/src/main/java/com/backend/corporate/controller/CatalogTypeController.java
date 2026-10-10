package com.backend.corporate.controller;

import com.backend.corporate.dto.*;
import com.backend.corporate.service.CorporateDataService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog-types")
public class CatalogTypeController {
  private final CorporateDataService service;

  public CatalogTypeController(CorporateDataService service) {
    this.service = service;
  }

  @GetMapping
  public Page<CatalogTypeView> list(Pageable pageable) {
    return service.listTypes(pageable);
  }

  @GetMapping("/{id}")
  public CatalogTypeView get(@PathVariable Long id) {
    return service.getType(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CatalogTypeView create(@Valid @RequestBody CatalogTypeRequest request) {
    return service.saveType(null, request);
  }

  @PutMapping("/{id}")
  public CatalogTypeView update(
      @PathVariable Long id, @Valid @RequestBody CatalogTypeRequest request) {
    return service.saveType(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.deleteType(id);
  }
}
