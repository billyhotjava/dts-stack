package com.yuzhi.dts.platform.service.services;

import com.yuzhi.dts.platform.domain.service.SvcApi;
import com.yuzhi.dts.platform.repository.service.SvcApiRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class JpaServiceAssetIdentityReadAdapter implements ServiceAssetIdentityReadPort {

    private final SvcApiRepository apis;

    public JpaServiceAssetIdentityReadAdapter(SvcApiRepository apis) {
        this.apis = apis;
    }

    @Override
    public Optional<ApiAsset> findApiById(UUID id) {
        return id == null ? Optional.empty() : apis.findById(id).map(this::view);
    }

    @Override
    public Optional<ApiAsset> findApiByCode(String code) {
        return !StringUtils.hasText(code) ? Optional.empty() : apis.findFirstByCodeIgnoreCase(code.trim()).map(this::view);
    }

    private ApiAsset view(SvcApi api) {
        return new ApiAsset(api.getId(), api.getCode());
    }
}
