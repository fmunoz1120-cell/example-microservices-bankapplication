package com.backend.client;

import static org.assertj.core.api.Assertions.*;

import com.backend.client.model.Client;
import org.junit.jupiter.api.Test;

class SampleTest {
  @Test
  void clientInheritsPersonAndCanBeDeactivated() {
    Client client = new Client();
    client.completeName = "Ana Pérez";
    client.dni = "1234567890";
    assertThat(client).isInstanceOf(com.backend.client.model.Person.class);
    assertThat(client.completeName).isEqualTo("Ana Pérez");
    assertThat(client.isActive).isTrue();
    client.deactivate();
    assertThat(client.isActive).isFalse();
  }
}
