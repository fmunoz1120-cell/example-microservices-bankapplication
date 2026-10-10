package com.backend.client.controller;

import com.backend.client.dto.*;
import com.backend.client.service.ClientService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clients")
public class ClientController {
  private final ClientService service;

  public ClientController(ClientService s) {
    service = s;
  }

  @GetMapping
  public Page<ClientView> list(Pageable p) {
    return service.list(p);
  }

  @GetMapping("/{id}")
  public ClientView get(@PathVariable Long id) {
    return ClientView.of(service.get(id));
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public ClientView create(@Valid @RequestBody ClientRequest r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  public ClientView put(@PathVariable Long id, @Valid @RequestBody ClientRequest r) {
    return service.update(id, r);
  }

  @PatchMapping("/{id}")
  public ClientView patch(@PathVariable Long id, @RequestBody Map<String, Object> r) {
    return service.patch(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
