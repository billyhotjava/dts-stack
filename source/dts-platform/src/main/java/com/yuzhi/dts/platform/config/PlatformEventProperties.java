package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.platform.events")
public class PlatformEventProperties {

    private final Kafka kafka = new Kafka();

    public Kafka getKafka() {
        return kafka;
    }

    public static class Kafka {
        private boolean enabled = false;
        private String topic = "dts.platform.events";
        private int batchSize = 50;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
