package com.backend.client.service;

import com.backend.client.dto.*;
import com.backend.client.exception.*;
import com.backend.client.model.*;
import com.backend.client.repository.*;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ClientService {
  private final ClientRepository repo;
  private final OutboxRepository outbox;
  private final ObjectMapper json;
  private final Validator validator;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public ClientService(ClientRepository r, OutboxRepository o, ObjectMapper j, Validator v) {
    repo = r;
    outbox = o;
    json = j;
    validator = v;
  }

  public Client get(Long id) {
    return repo.findById(id).orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
  }

  public Page<ClientView> list(Pageable p) {
    return repo.findAll(p).map(ClientView::of);
  }

  public ClientView create(ClientRequest r) {
    return save(new Client(), r, true);
  }

  public ClientView update(Long id, ClientRequest r) {
    return save(get(id), r, true);
  }

  public ClientView patch(Long id, Map<String, Object> patch) {
    var allowed =
        Set.of(
            "firstName",
            "lastName",
            "completeName",
            "dniTypeId",
            "dni",
            "gender",
            "age",
            "address",
            "phone",
            "password",
            "isActive");
    if (!allowed.containsAll(patch.keySet()) || patch.values().stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException();
    Client c = get(id);
    var data = new HashMap<String, Object>();
    data.put("firstName", c.firstName);
    data.put("lastName", c.lastName);
    data.put("completeName", c.completeName);
    data.put("dniTypeId", c.dniTypeId);
    data.put("dni", c.dni);
    data.put("gender", c.gender);
    data.put("age", c.age);
    data.put("address", c.address);
    data.put("phone", c.phone);
    data.put("password", "unchanged-password");
    data.put("isActive", c.isActive);
    data.putAll(patch);
    return save(c, json.convertValue(data, ClientRequest.class), patch.containsKey("password"));
  }

  private ClientView save(Client c, ClientRequest r, boolean password) {
    if (!validator.validate(r).isEmpty()
        || r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException();
    c.firstName = r.firstName();
    c.lastName = r.lastName();
    c.completeName = r.completeName();
    c.dniTypeId = r.dniTypeId();
    c.dni = r.dni();
    c.gender = r.gender();
    c.age = r.age();
    c.address = r.address();
    c.phone = r.phone();
    c.updateActiveState(r.isActive());
    if (password) c.password = encoder.encode(r.password());
    repo.saveAndFlush(c);
    emit(c);
    return ClientView.of(c);
  }

  public void delete(Long id) {
    Client c = get(id);
    c.deactivate();
    repo.flush();
    emit(c);
  }

  private void emit(Client c) {
    var e = new OutboxEvent();
    e.id = UUID.randomUUID();
    e.clientId = c.id;
    e.payload =
        json.writeValueAsString(
            new ClientEvent(1, e.id, c.id, c.version, c.completeName, c.isActive));
    outbox.save(e);
  }
}
