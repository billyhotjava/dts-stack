package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.catalog.CatalogDataProductRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper.CATALOG_MAINTAINER_EXPRESSION;

@RestController
@RequestMapping("/api/catalog")
public class CatalogDataProductResource {

	private final CatalogDataProductRepository repo;
	private final AuditService audit;

	public CatalogDataProductResource(CatalogDataProductRepository repo, AuditService audit) {
		this.repo = repo;
		this.audit = audit;
	}

	@GetMapping("/data-products")
	@Transactional(readOnly = true)
	public ApiResponse<Map<String, Object>> list(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {
		Page<CatalogDataProduct> p = repo.findAll(PageRequest.of(page, size, Sort.by("createdDate").descending()));
		return ApiResponses.ok(Map.of("content", p.getContent(), "total", p.getTotalElements()));
	}

	@GetMapping("/data-products/{id}")
	@Transactional(readOnly = true)
	public ApiResponse<CatalogDataProduct> get(@PathVariable UUID id) {
		return ApiResponses.ok(repo.findById(id).orElseThrow());
	}

	@PostMapping("/data-products")
	@Transactional
	@PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
	public ApiResponse<CatalogDataProduct> create(@RequestBody CatalogDataProduct body) {
		body.setId(null);  // 防止客户端指定 id
		CatalogDataProduct saved = repo.save(body);
		audit.auditAction("CATALOG_DATA_PRODUCT_CREATE", AuditStage.SUCCESS, saved.getId().toString(), null);
		return ApiResponses.ok(saved);
	}

	@PutMapping("/data-products/{id}")
	@Transactional
	@PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
	public ApiResponse<CatalogDataProduct> update(@PathVariable UUID id, @RequestBody CatalogDataProduct body) {
		CatalogDataProduct existing = repo.findById(id).orElseThrow();
		existing.setName(body.getName());
		existing.setCode(body.getCode());
		existing.setOwnerDept(body.getOwnerDept());
		existing.setDescription(body.getDescription());
		existing.setDatasetIds(body.getDatasetIds());
		existing.setIndicatorCodes(body.getIndicatorCodes());
		existing.setClassification(body.getClassification());
		existing.setFreshnessSla(body.getFreshnessSla());
		existing.setLifecycleStatus(body.getLifecycleStatus());
		existing.setVisibility(body.getVisibility());
		existing.setConsumerEntry(body.getConsumerEntry());
		existing.setStatus(body.getStatus());
		CatalogDataProduct saved = repo.save(existing);
		audit.auditAction("CATALOG_DATA_PRODUCT_UPDATE", AuditStage.SUCCESS, id.toString(), null);
		return ApiResponses.ok(saved);
	}

	@DeleteMapping("/data-products/{id}")
	@Transactional
	@PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
	public ApiResponse<Boolean> delete(@PathVariable UUID id) {
		repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据产品不存在"));
		repo.deleteById(id);
		audit.auditAction("CATALOG_DATA_PRODUCT_DELETE", AuditStage.SUCCESS, id.toString(), null);
		return ApiResponses.ok(Boolean.TRUE);
	}
}
