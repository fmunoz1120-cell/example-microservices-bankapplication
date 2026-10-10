package com.backend.corporate;

import static org.assertj.core.api.Assertions.assertThat;

import com.backend.corporate.model.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

class CorporateModelTest {
  @Test
  void mapsCorporateTablesAndReferences() {
    var registry =
        new StandardServiceRegistryBuilder()
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .build();
    try {
      var metadata =
          new MetadataSources(registry)
              .addAnnotatedClass(CatalogType.class)
              .addAnnotatedClass(CatalogValue.class)
              .addAnnotatedClass(WorkArea.class)
              .buildMetadata();
      assertThat(metadata.getEntityBinding(CatalogType.class.getName()).getTable().getName())
          .isEqualTo("catalog_types");
      assertThat(metadata.getEntityBinding(CatalogType.class.getName()).getTable().getColumns())
          .extracting(column -> column.getName())
          .contains("catalog_name", "created_at", "updated_at")
          .doesNotContain("catalog_code");
      assertThat(metadata.getEntityBinding(CatalogValue.class.getName()).getTable().getName())
          .isEqualTo("catalog_values");
      assertThat(metadata.getEntityBinding(WorkArea.class.getName()).getTable().getColumns())
          .extracting(column -> column.getName())
          .contains("area_type_id", "created_at", "updated_at");
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }
}
