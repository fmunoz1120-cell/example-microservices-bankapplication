package com.backend.account.controller;

import com.backend.account.dto.*;
import com.backend.account.service.TransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
  private final TransactionService service;

  public TransactionController(TransactionService s) {
    service = s;
  }

  @GetMapping
  public Page<TransactionView> list(@RequestParam(required = false) Long accountId, Pageable p) {
    return service.list(accountId, p);
  }

  @GetMapping("/{id}")
  public TransactionView get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public TransactionView create(@Valid @RequestBody TransactionRequest r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  public TransactionView put(@PathVariable Long id, @Valid @RequestBody TransactionRequest r) {
    return service.update(id, r);
  }

  @PatchMapping("/{id}")
  public TransactionView patch(@PathVariable Long id, @RequestBody Map<String, Object> r) {
    return service.patch(id, r);
  }

  @DeleteMapping("/{id}")
  public TransactionView delete(@PathVariable Long id) {
    return service.delete(id);
  }

  @GetMapping("/clients/{clientId}/report")
  public AccountReport report(
      @PathVariable Long clientId,
      @RequestParam LocalDate dateTransactionStart,
      @RequestParam LocalDate dateTransactionEnd) {
    return service.report(clientId, dateTransactionStart, dateTransactionEnd);
  }
}
