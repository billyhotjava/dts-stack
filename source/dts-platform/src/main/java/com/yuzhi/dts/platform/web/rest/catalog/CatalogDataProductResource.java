package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct;
import com.yuzhi.dts.platform.repository.catalog.CatalogDataProductRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
		CatalogDataProduct saved = repo.save(body);
		audit.audit("CREATE", "catalog.data-product", saved.getId().toString());
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
		existing.setStatus(body.getStatus());
		CatalogDataProduct saved = repo.save(existing);
		audit.audit("UPDATE", "catalog.data-product", id.toString());
		return ApiResponses.ok(saved);
	}

	@DeleteMapping("/data-products/{id}")
	@Transactional
	@PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
	public ApiResponse<Boolean> delete(@PathVariable UUID id) {
		repo.deleteById(id);
		audit.audit("DELETE", "catalog.data-product", id.toString());
		return ApiResponses.ok(Boolean.TRUE);
	}
}
