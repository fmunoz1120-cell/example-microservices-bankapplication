package com.backend.account.controller;

import com.backend.account.dto.*;
import com.backend.account.service.AccountService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
  private final AccountService service;

  public AccountController(AccountService s) {
    service = s;
  }

  @GetMapping
  public Page<AccountView> list(Pageable p) {
    return service.list(p);
  }

  @GetMapping("/{id}")
  public AccountView get(@PathVariable Long id) {
    return AccountView.of(service.get(id));
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public AccountView create(@Valid @RequestBody AccountRequest r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  public AccountView put(@PathVariable Long id, @Valid @RequestBody AccountRequest r) {
    return service.update(id, r);
  }

  @PatchMapping("/{id}")
  public AccountView patch(@PathVariable Long id, @RequestBody Map<String, Object> r) {
    return service.patch(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
