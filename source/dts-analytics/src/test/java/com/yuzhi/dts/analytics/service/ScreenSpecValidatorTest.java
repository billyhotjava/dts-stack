package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.web.rest.errors.ScreenSpecValidationException;
import org.junit.jupiter.api.Test;

class ScreenSpecValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validateForWrite_visibilityRuleInvalidMode_rejected() throws Exception {
        ScreenSpecValidator validator = new ScreenSpecValidator();
        var payload = objectMapper.readTree("""
                {
                  "schemaVersion": 2,
                  "width": 1920,
                  "height": 1080,
                  "components": [
                    {
                      "id": "c1",
                      "type": "line-chart",
                      "x": 0,
                      "y": 0,
                      "width": 300,
                      "height": 200,
                      "config": {
                        "visibilityRuleEnabled": true,
                        "visibilityVariableKey": "tabKey",
                        "visibilityMatchMode": "regexp"
                      }
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> validator.validateForWrite(payload))
                .isInstanceOf(ScreenSpecValidationException.class)
                .hasMessageContaining("visibilityMatchMode");
    }

    @Test
    void validateForWrite_visibilityRuleContainsMode_accepted() throws Exception {
        ScreenSpecValidator validator = new ScreenSpecValidator();
        var payload = objectMapper.readTree("""
                {
                  "schemaVersion": 2,
                  "width": 1920,
                  "height": 1080,
                  "components": [
                    {
                      "id": "c1",
                      "type": "line-chart",
                      "x": 0,
                      "y": 0,
                      "width": 300,
                      "height": 200,
                      "config": {
                        "visibilityRuleEnabled": true,
                        "visibilityVariableKey": "tabKey",
                        "visibilityMatchMode": "contains",
                        "visibilityMatchValues": ["prod"]
                      }
                    }
                  ]
                }
                """);

        ScreenSpecValidator.ValidationResult result = validator.validateForWrite(payload);
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void validateForWrite_tabSwitcherAndCarousel_accepted() throws Exception {
        ScreenSpecValidator validator = new ScreenSpecValidator();
        var payload = objectMapper.readTree("""
                {
                  "schemaVersion": 2,
                  "width": 1920,
                  "height": 1080,
                  "components": [
                    {
                      "id": "c-tab",
                      "type": "tab-switcher",
                      "x": 0,
                      "y": 0,
                      "width": 420,
                      "height": 80,
                      "config": {
                        "variableKey": "tabKey",
                        "options": [{"label":"总览","value":"overview"}]
                      }
                    },
                    {
                      "id": "c-carousel",
                      "type": "carousel",
                      "x": 30,
                      "y": 120,
                      "width": 560,
                      "height": 240
                    }
                  ]
                }
                """);

        ScreenSpecValidator.ValidationResult result = validator.validateForWrite(payload);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void validateForWrite_interactionInvalidTransform_rejected() throws Exception {
        ScreenSpecValidator validator = new ScreenSpecValidator();
        var payload = objectMapper.readTree("""
                {
                  "schemaVersion": 2,
                  "width": 1920,
                  "height": 1080,
                  "components": [
                    {
                      "id": "c1",
                      "type": "line-chart",
                      "x": 0,
                      "y": 0,
                      "width": 300,
                      "height": 200,
                      "interaction": {
                        "enabled": true,
                        "event": "click",
                        "mappings": [
                          {
                            "variableKey": "projectCode",
                            "sourcePath": "seriesName",
                            "transform": "trim"
                          }
                        ]
                      }
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> validator.validateForWrite(payload))
                .isInstanceOf(ScreenSpecValidationException.class)
                .hasMessageContaining("transform");
    }

    @Test
    void validateForWrite_interactionMappingsWithTransformAndFallback_accepted() throws Exception {
        ScreenSpecValidator validator = new ScreenSpecValidator();
        var payload = objectMapper.readTree("""
                {
                  "schemaVersion": 2,
                  "width": 1920,
                  "height": 1080,
                  "components": [
                    {
                      "id": "c1",
                      "type": "line-chart",
                      "x": 0,
                      "y": 0,
                      "width": 300,
                      "height": 200,
                      "interaction": {
                        "enabled": true,
                        "event": "click",
                        "mappings": [
                          {
                            "variableKey": "projectCode",
                            "sourcePath": "seriesName",
                            "transform": "uppercase",
                            "fallbackValue": "UNKNOWN"
                          },
                          {
                            "variableKey": "ownerName",
                            "sourcePath": "value",
                            "fallbackValue": "N/A"
                          }
                        ]
                      }
                    }
                  ]
                }
                """);

        ScreenSpecValidator.ValidationResult result = validator.validateForWrite(payload);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void validateForWrite_ganttChart_accepted() throws Exception {
        ScreenSpecValidator validator = new ScreenSpecValidator();
        var payload = objectMapper.readTree("""
                {
                  "schemaVersion": 2,
                  "width": 1920,
                  "height": 1080,
                  "components": [
                    {
                      "id": "c-gantt",
                      "type": "gantt-chart",
                      "x": 0,
                      "y": 0,
                      "width": 640,
                      "height": 320,
                      "config": {
                        "title": "项目执行监控"
                      }
                    }
                  ]
                }
                """);

        ScreenSpecValidator.ValidationResult result = validator.validateForWrite(payload);
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
}
