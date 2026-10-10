package com.backend.account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.backend.account.dto.*;
import com.backend.account.exception.*;
import com.backend.account.model.*;
import com.backend.account.repository.*;
import com.backend.account.service.*;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

class TransactionServiceTest {
  AccountService accounts;
  TransactionRepository repo;
  TransactionService service;
  Account account;

  @BeforeEach
  void setup() {
    accounts = mock(AccountService.class);
    repo = mock(TransactionRepository.class);
    service =
        new TransactionService(
            accounts,
            mock(AccountRepository.class),
            repo,
            mock(ClientProjectionRepository.class),
            Validation.buildDefaultValidatorFactory().getValidator(),
            JsonMapper.builder().build());
    account = new Account();
    account.id = 1L;
    account.clientId = 2L;
    account.isActive = true;
    account.balance = new BigDecimal("100.00");
    when(accounts.lock(1L)).thenReturn(account);
    when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void depositsAndWithdrawalsUpdateBalance() {
    assertThat(
            service
                .create(new TransactionRequest(1L, 301L, new BigDecimal("25.00"), 1L))
                .balance())
        .isEqualByComparingTo("125.00");
    assertThat(
            service
                .create(new TransactionRequest(1L, 302L, new BigDecimal("-40.00"), 1L))
                .balance())
        .isEqualByComparingTo("85.00");
  }

  @Test
  void refusesOverdraftWithoutWriting() {
    assertThatThrownBy(
            () ->
                service.create(new TransactionRequest(1L, 302L, new BigDecimal("-101.00"), 1L)))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Saldo no disponible");
    assertThat(account.balance).isEqualByComparingTo("100");
    verifyNoInteractions(repo);
  }

  @Test
  void validatesSignZeroAndPrecision() {
    for (var r :
        java.util.List.of(
            new TransactionRequest(1L, 301L, BigDecimal.ZERO, 1L),
            new TransactionRequest(1L, 301L, new BigDecimal("-1"), 1L),
            new TransactionRequest(1L, 302L, BigDecimal.ONE, 1L),
            new TransactionRequest(1L, 301L, new BigDecimal("1.001"), 1L)))
      assertThatThrownBy(() -> service.create(r)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesInactiveAccount() {
    account.isActive = false;
    assertThatThrownBy(() -> service.create(new TransactionRequest(1L, 301L, BigDecimal.ONE, 1L)))
        .hasMessage("Cuenta inactiva");
    verifyNoInteractions(repo);
  }
}
