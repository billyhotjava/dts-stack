package com.yuzhi.dts.platform.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.PlatformEventProperties;
import com.yuzhi.dts.platform.domain.event.PlatformEventOutbox;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlatformEventKafkaDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PlatformEventKafkaDispatcher.class);

    private final PlatformEventProperties properties;
    private final PlatformEventOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    public PlatformEventKafkaDispatcher(
        PlatformEventProperties properties,
        PlatformEventOutboxService outboxService,
        ObjectMapper objectMapper,
        ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider
    ) {
        this.properties = properties;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
    }

    @Scheduled(fixedDelayString = "${dts.platform.events.kafka.dispatch-interval-ms:10000}")
    public void dispatchPendingEvents() {
        if (!properties.getKafka().isEnabled()) {
            return;
        }
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("Platform event Kafka dispatcher enabled but KafkaTemplate is not available");
            return;
        }
        String topic = properties.getKafka().getTopic();
        if (!StringUtils.hasText(topic)) {
            log.warn("Platform event Kafka dispatcher enabled but topic is empty");
            return;
        }
        List<PlatformEventOutbox> events = outboxService.nextPendingForDispatch();
        for (PlatformEventOutbox event : events) {
            try {
                String value = objectMapper.writeValueAsString(outboxService.toDispatchDto(event));
                kafkaTemplate.send(topic, event.getEventId(), value).get(5, TimeUnit.SECONDS);
                outboxService.markDispatched(event);
            } catch (Exception ex) {
                outboxService.markDispatchFailed(event, ex);
                log.warn("Failed to dispatch platform event {} to Kafka topic {}: {}", event.getEventId(), topic, ex.getMessage());
            }
        }
    }
}
