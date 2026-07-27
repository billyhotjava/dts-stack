package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import org.junit.jupiter.api.Test;

class ModelSpecLayerResolverContractTest {

    @Test
    void warehousePlanningCanReuseTheCanonicalModelTypeLayerResolver() {
        assertThat(ModelSpecContract.targetLayer(ModelType.DIMENSION)).isEqualTo(Layer.DWD);
        assertThat(ModelSpecContract.targetLayer(ModelType.FACT)).isEqualTo(Layer.DWD);
        assertThat(ModelSpecContract.targetLayer(ModelType.SUMMARY)).isEqualTo(Layer.DWS);
        assertThat(ModelSpecContract.targetLayer(ModelType.APPLICATION)).isEqualTo(Layer.ADS);
    }
}
