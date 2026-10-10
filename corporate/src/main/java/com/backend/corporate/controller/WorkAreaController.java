package com.backend.corporate.controller;

import com.backend.corporate.dto.*;
import com.backend.corporate.service.CorporateDataService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work-areas")
public class WorkAreaController {
  private final CorporateDataService service;

  public WorkAreaController(CorporateDataService service) {
    this.service = service;
  }

  @GetMapping
  public Page<WorkAreaView> list(Pageable pageable) {
    return service.listAreas(pageable);
  }

  @GetMapping("/{id}")
  public WorkAreaView get(@PathVariable Long id) {
    return service.getArea(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WorkAreaView create(@Valid @RequestBody WorkAreaRequest request) {
    return service.saveArea(null, request);
  }

  @PutMapping("/{id}")
  public WorkAreaView update(
      @PathVariable Long id, @Valid @RequestBody WorkAreaRequest request) {
    return service.saveArea(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.deleteArea(id);
  }
}
