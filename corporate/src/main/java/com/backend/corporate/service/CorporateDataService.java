package com.backend.corporate.service;

import com.backend.corporate.dto.*;
import com.backend.corporate.exception.NotFoundException;
import com.backend.corporate.model.*;
import com.backend.corporate.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CorporateDataService {
  private static final Long WORK_AREA_TYPE_CATALOG_ID = 4L;
  private final CatalogTypeRepository types;
  private final CatalogValueRepository values;
  private final WorkAreaRepository areas;

  public CorporateDataService(
      CatalogTypeRepository types, CatalogValueRepository values, WorkAreaRepository areas) {
    this.types = types;
    this.values = values;
    this.areas = areas;
  }

  public Page<CatalogTypeView> listTypes(Pageable pageable) {
    return types.findAll(pageable).map(CatalogTypeView::of);
  }

  public CatalogTypeView getType(Long id) {
    return CatalogTypeView.of(findType(id));
  }

  public CatalogTypeView saveType(Long id, CatalogTypeRequest request) {
    CatalogType value = id == null ? new CatalogType() : findType(id);
    value.name = request.name();
    return CatalogTypeView.of(types.save(value));
  }

  public void deleteType(Long id) {
    types.delete(findType(id));
    types.flush();
  }

  public Page<CatalogValueView> listValues(Long catalogTypeId, Pageable pageable) {
    return (catalogTypeId == null
            ? values.findAll(pageable)
            : values.findByCatalogTypeId(catalogTypeId, pageable))
        .map(CatalogValueView::of);
  }

  public CatalogValueView getValue(Long id) {
    return CatalogValueView.of(findValue(id));
  }

  public CatalogValueView saveValue(Long id, CatalogValueRequest request) {
    CatalogValue value = id == null ? new CatalogValue() : findValue(id);
    value.catalogType = findType(request.catalogTypeId());
    value.code = request.code();
    value.name = request.name();
    return CatalogValueView.of(values.save(value));
  }

  public void deleteValue(Long id) {
    values.delete(findValue(id));
    values.flush();
  }

  public Page<WorkAreaView> listAreas(Pageable pageable) {
    return areas.findAll(pageable).map(WorkAreaView::of);
  }

  public WorkAreaView getArea(Long id) {
    return WorkAreaView.of(findArea(id));
  }

  public WorkAreaView saveArea(Long id, WorkAreaRequest request) {
    WorkArea area = id == null ? new WorkArea() : findArea(id);
    area.name = request.name();
    area.address = request.address();
    area.city = request.city();
    area.areaType = findValue(request.areaTypeId());
    if (!WORK_AREA_TYPE_CATALOG_ID.equals(area.areaType.catalogType.id)) {
      throw new IllegalArgumentException("areaTypeId must belong to the work area type catalog");
    }
    return WorkAreaView.of(areas.save(area));
  }

  public void deleteArea(Long id) {
    areas.delete(findArea(id));
  }

  private CatalogType findType(Long id) {
    return types.findById(id).orElseThrow(() -> new NotFoundException("Catalog type not found"));
  }

  private CatalogValue findValue(Long id) {
    return values.findById(id).orElseThrow(() -> new NotFoundException("Catalog value not found"));
  }

  private WorkArea findArea(Long id) {
    return areas.findById(id).orElseThrow(() -> new NotFoundException("Work area not found"));
  }
}
