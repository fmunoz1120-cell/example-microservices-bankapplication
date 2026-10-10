package com.backend.account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.backend.account.model.Account;
import com.backend.account.repository.*;
import com.backend.account.service.AccountService;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.Optional;
import org.hibernate.proxy.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AccountServiceTest {
  @Test
  void lockedAccountIsUnproxiedBeforeFieldAccess() {
    var repo = mock(AccountRepository.class);
    Account real = new Account();
    real.id = 1L;
    real.balance = new BigDecimal("100");
    Account proxy = mock(Account.class, withSettings().extraInterfaces(HibernateProxy.class));
    when(((HibernateProxy) proxy).asHibernateProxy()).thenReturn((HibernateProxy) proxy);
    var initializer = mock(LazyInitializer.class);
    when(((HibernateProxy) proxy).getHibernateLazyInitializer()).thenReturn(initializer);
    when(initializer.getImplementation()).thenReturn(real);
    when(repo.lock(1L)).thenReturn(Optional.of(proxy));
    var service =
        new AccountService(
            repo,
            mock(TransactionRepository.class),
            mock(ClientProjectionRepository.class),
            Validation.buildDefaultValidatorFactory().getValidator(),
            JsonMapper.builder().build());
    assertThat(service.lock(1L)).isSameAs(real);
    assertThat(service.lock(1L).balance).isEqualByComparingTo("100");
  }

  @Test
  void inactiveStateCreatesDeletionTimestampAndReactivationClearsIt() {
    var repo = mock(AccountRepository.class);
    var projections = mock(ClientProjectionRepository.class);
    Account a = new Account();
    a.id = 1L;
    a.clientId = 2L;
    a.isActive = true;
    var client = new com.backend.account.model.ClientProjection();
    client.id = 2L;
    client.active = true;
    when(repo.lock(1L)).thenReturn(Optional.of(a));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    when(projections.findById(2L)).thenReturn(Optional.of(client));
    var service =
        new AccountService(
            repo,
            mock(TransactionRepository.class),
            projections,
            Validation.buildDefaultValidatorFactory().getValidator(),
            JsonMapper.builder().build());
    service.update(
        1L,
        new com.backend.account.dto.AccountRequest("123", 201L, 2L, BigDecimal.ZERO, false, 1L));
    var deletedAt = a.deletedAt;
    assertThat(deletedAt).isNotNull();
    assertThat(a.isActive).isFalse();
    service.delete(1L);
    assertThat(a.deletedAt).isEqualTo(deletedAt);
    service.update(
        1L,
        new com.backend.account.dto.AccountRequest("123", 201L, 2L, BigDecimal.ZERO, true, 1L));
    assertThat(a.deletedAt).isNull();
    assertThat(a.isActive).isTrue();
  }
}
