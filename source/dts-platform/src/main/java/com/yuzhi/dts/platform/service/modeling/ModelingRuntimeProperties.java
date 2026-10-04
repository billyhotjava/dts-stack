package com.yuzhi.dts.platform.service.modeling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Feature switch for the external Addax -> Airflow hand-off. */
@Component
@ConfigurationProperties(prefix = "dts.modeling.runtime")
public class ModelingRuntimeProperties {

    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
