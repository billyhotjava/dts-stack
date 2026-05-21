package com.yuzhi.dts.platform.repository.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.domain.governance.StdCodeMapping;
import com.yuzhi.dts.platform.domain.governance.StdCodeValue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class StdCodeIdentityPersistenceIT {

    @Autowired
    private StdCodeDirectoryRepository directoryRepository;

    @Autowired
    private StdCodeValueRepository valueRepository;

    @Autowired
    private StdCodeMappingRepository mappingRepository;

    @Test
    void savesCodeValueAndMappingUsingDatabaseIdentityColumns() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String codeTypeId = "it_" + suffix;

        StdCodeDirectory directory = new StdCodeDirectory();
        directory.setCodeTypeId(codeTypeId);
        directory.setCodeTypeCode("IT_" + suffix);
        directory.setCodeTypeName("IT 码表");
        directoryRepository.saveAndFlush(directory);

        StdCodeValue value = new StdCodeValue();
        value.setCodeTypeId(codeTypeId);
        value.setCodeValue("m");
        value.setCodeName("男");
        valueRepository.saveAndFlush(value);

        StdCodeMapping mapping = new StdCodeMapping();
        mapping.setCodeTypeId(codeTypeId);
        mapping.setSourceSys("it");
        mapping.setSrcCode("male");
        mapping.setStdCode("m");
        mappingRepository.saveAndFlush(mapping);

        assertThat(value.getItemId()).isNotNull();
        assertThat(mapping.getMapId()).isNotNull();
    }
}
