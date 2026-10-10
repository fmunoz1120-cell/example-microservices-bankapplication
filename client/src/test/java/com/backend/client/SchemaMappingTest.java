package com.backend.client;

import static org.assertj.core.api.Assertions.*;

import com.backend.client.model.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

class SchemaMappingTest {
  @Test
  void hibernateMapsTheRenamedSchemaAndAuditFields() {
    var registry =
        new StandardServiceRegistryBuilder()
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .build();
    try {
      var metadata =
          new MetadataSources(registry)
              .addAnnotatedClass(Person.class)
              .addAnnotatedClass(Client.class)
              .addAnnotatedClass(OutboxEvent.class)
              .buildMetadata();

      var person = metadata.getEntityBinding(Person.class.getName()).getTable();
      var client = metadata.getEntityBinding(Client.class.getName()).getTable();
      assertThat(person.getName()).isEqualTo("persons");
      assertThat(client.getName()).isEqualTo("clients");
      assertThat(person.getColumns())
          .extracting(c -> c.getName())
          .contains(
              "first_name",
              "last_name",
              "complete_name",
              "dni_type_id",
              "created_at",
              "updated_at",
              "version");
      assertThat(client.getColumns())
          .extracting(c -> c.getName())
          .contains("deleted_at", "id")
          .doesNotContain("name");
      assertThat(metadata.getEntityBinding(OutboxEvent.class.getName()).getTable().getColumns())
          .extracting(c -> c.getName())
          .contains("is_published", "created_at", "updated_at");

    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }
}
