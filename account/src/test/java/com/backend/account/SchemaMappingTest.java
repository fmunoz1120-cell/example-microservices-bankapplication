package com.backend.account;

import static org.assertj.core.api.Assertions.*;

import com.backend.account.model.*;
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
              .addAnnotatedClass(Account.class)
              .addAnnotatedClass(Transaction.class)
              .addAnnotatedClass(ClientProjection.class)
              .addAnnotatedClass(ProcessedEvent.class)
              .buildMetadata();

      var account = metadata.getEntityBinding(Account.class.getName()).getTable();
      var transaction = metadata.getEntityBinding(Transaction.class.getName()).getTable();
      assertThat(account.getColumns())
          .extracting(c -> c.getName())
          .contains(
              "account_number",
              "account_type_id",
              "account_created_at",
              "work_area_id",
              "client_projection_id",
              "created_at",
              "updated_at",
              "deleted_at")
          .doesNotContain("number", "type");
      assertThat(transaction.getColumns())
          .extracting(c -> c.getName())
          .contains(
              "transaction_date",
              "transaction_type_id",
              "work_area_id",
              "is_reversed",
              "reversed_transaction_id",
              "replaced_transaction_id",
              "created_at",
              "updated_at")
          .doesNotContain("date", "type");
      assertThat(metadata.getEntityBinding(ClientProjection.class.getName()).getTable().getName())
          .isEqualTo("client_projections");

    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }
}
