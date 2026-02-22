package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ScreenServerRenderExportServiceTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void renderPng_respectsPixelRatioAndProducesNonTransparentImage() throws Exception {
        ScreenServerRenderExportService service = new ScreenServerRenderExportService();
        JsonNode spec = objectMapper.readTree("""
                {
                  "name": "导出渲染测试",
                  "width": 1280,
                  "height": 720,
                  "theme": "legacy-dark",
                  "backgroundColor": "#0d1b2a",
                  "components": [
                    {
                      "id": "c1",
                      "type": "line-chart",
                      "name": "趋势图",
                      "x": 40,
                      "y": 90,
                      "width": 560,
                      "height": 260,
                      "zIndex": 1,
                      "visible": true,
                      "config": {
                        "title": "产量趋势",
                        "xAxisData": ["一", "二", "三", "四", "五", "六"],
                        "series": [{ "name": "产量", "data": [12, 18, 15, 22, 19, 24] }]
                      }
                    },
                    {
                      "id": "c2",
                      "type": "table",
                      "name": "明细表",
                      "x": 640,
                      "y": 90,
                      "width": 580,
                      "height": 260,
                      "zIndex": 2,
                      "visible": true,
                      "config": {
                        "header": ["设备", "状态", "产量"],
                        "data": [["A-01", "在线", "120"], ["A-02", "告警", "98"], ["A-03", "在线", "133"]]
                      }
                    },
                    {
                      "id": "c3",
                      "type": "number-card",
                      "name": "总产量",
                      "x": 40,
                      "y": 390,
                      "width": 300,
                      "height": 180,
                      "zIndex": 3,
                      "visible": true,
                      "config": {
                        "title": "总产量",
                        "prefix": "",
                        "suffix": " 件",
                        "value": 12345
                      }
                    }
                  ]
                }
                """);

        byte[] png = service.renderPng(spec, true, "DTS", 2.0d);
        assertThat(png).isNotEmpty();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(2560);
        assertThat(image.getHeight()).isEqualTo(1440);

        int alpha = (image.getRGB(image.getWidth() / 2, image.getHeight() / 2) >>> 24) & 0xff;
        assertThat(alpha).isGreaterThan(0);
    }

    @Test
    void renderPdf_outputsPdfBinary() throws Exception {
        ScreenServerRenderExportService service = new ScreenServerRenderExportService();
        JsonNode spec = objectMapper.readTree("""
                {
                  "name": "PDF测试",
                  "width": 800,
                  "height": 450,
                  "backgroundColor": "#f6f7f9",
                  "theme": "glacier",
                  "components": [
                    {
                      "id": "t1",
                      "type": "title",
                      "name": "标题",
                      "x": 40,
                      "y": 80,
                      "width": 320,
                      "height": 80,
                      "zIndex": 1,
                      "visible": true,
                      "config": { "text": "商务主题导出" }
                    }
                  ]
                }
                """);

        byte[] pdf = service.renderPdf(spec, false, "", 1.5d);
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, Math.min(4, pdf.length))).isEqualTo("%PDF");
    }

    @Test
    void renderPng_supportsAdvancedComponentTypes() throws Exception {
        ScreenServerRenderExportService service = new ScreenServerRenderExportService();
        JsonNode spec = objectMapper.readTree("""
                {
                  "name": "高级组件导出",
                  "width": 1366,
                  "height": 768,
                  "theme": "legacy-dark",
                  "backgroundColor": "#0d1b2a",
                  "components": [
                    {
                      "id": "g1",
                      "type": "gauge-chart",
                      "name": "综合得分",
                      "x": 40,
                      "y": 100,
                      "width": 300,
                      "height": 220,
                      "zIndex": 1,
                      "visible": true,
                      "config": { "value": 78 }
                    },
                    {
                      "id": "r1",
                      "type": "radar-chart",
                      "name": "能力雷达",
                      "x": 380,
                      "y": 100,
                      "width": 320,
                      "height": 220,
                      "zIndex": 2,
                      "visible": true,
                      "config": {
                        "data": [
                          { "name": "质量", "value": 88 },
                          { "name": "效率", "value": 76 },
                          { "name": "成本", "value": 69 },
                          { "name": "交付", "value": 81 },
                          { "name": "稳定", "value": 73 }
                        ]
                      }
                    },
                    {
                      "id": "p1",
                      "type": "progress-bar",
                      "name": "任务进度",
                      "x": 740,
                      "y": 100,
                      "width": 280,
                      "height": 120,
                      "zIndex": 3,
                      "visible": true,
                      "config": { "value": 64 }
                    },
                    {
                      "id": "d1",
                      "type": "digital-flop",
                      "name": "实时指标",
                      "x": 40,
                      "y": 360,
                      "width": 300,
                      "height": 150,
                      "zIndex": 4,
                      "visible": true,
                      "config": { "value": 45678 }
                    },
                    {
                      "id": "f1",
                      "type": "filter-date-range",
                      "name": "日期筛选",
                      "x": 380,
                      "y": 360,
                      "width": 320,
                      "height": 72,
                      "zIndex": 5,
                      "visible": true,
                      "config": { "label": "日期区间" }
                    },
                    {
                      "id": "m1",
                      "type": "image",
                      "name": "背景图",
                      "x": 740,
                      "y": 260,
                      "width": 300,
                      "height": 220,
                      "zIndex": 6,
                      "visible": true,
                      "config": { "src": "https://example.com/a.png" }
                    },
                    {
                      "id": "s1",
                      "type": "shape",
                      "name": "矩形装饰",
                      "x": 1080,
                      "y": 100,
                      "width": 220,
                      "height": 220,
                      "zIndex": 7,
                      "visible": true,
                      "config": { "shapeType": "rect" }
                    }
                  ]
                }
                """);

        byte[] png = service.renderPng(spec, false, "", 1.0d);
        assertThat(png).isNotEmpty();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(1366);
        assertThat(image.getHeight()).isEqualTo(768);
    }
}
