package com.insuscan.pipeline.support;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.pipeline.model.PipelineResult;
import com.insuscan.pipeline.model.PipelineWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class ScanReportWriter {

    private static final Logger log = LoggerFactory.getLogger(ScanReportWriter.class);

    private static final float CONFIDENCE_GOOD = 0.75f;
    private static final float CONFIDENCE_WEAK = 0.45f;

    @Value("${scan.report.dir:}")
    private String reportDir;

    @Value("${scan.report.enabled:true}")
    private boolean enabled;

    public void write(PipelineContext ctx, PipelineResult result) {
        if (!enabled || reportDir == null || reportDir.isBlank()) return;
        try {
            File folder = new File(reportDir, safe(ctx.getSessionId()));
            folder.mkdirs();
            File target = new File(folder, "report.html");
            Files.write(target.toPath(), render(ctx, result).getBytes(StandardCharsets.UTF_8));
            log.info("[Report] Written to {}", target.getAbsolutePath());
        } catch (Exception e) {
            log.warn("[Report] Failed to write report: {}", e.getMessage());
        }
    }

    private String render(PipelineContext ctx, PipelineResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        sb.append("<title>InsuScan Scan Report</title>").append(style()).append("</head><body>");
        sb.append(header(ctx, result));
        sb.append(gallery(folderImages(ctx)));
        sb.append(calibrationBlock(ctx));
        sb.append(stagesBlock(ctx));
        sb.append(itemsBlock(ctx));
        sb.append(warningsBlock(result));
        sb.append("</body></html>");
        return sb.toString();
    }

    private String style() {
        return "<style>"
            + ":root{--navy:#1A3B8C;--bg:#F2F3F5;--card:#FFFFFF;--line:#DDE1E8;--text:#1C1F24;--muted:#6B7280;"
            + "--good:#1B7F4B;--warn:#B26A00;--bad:#B3261E;}"
            + "*{box-sizing:border-box}"
            + "body{margin:0;padding:32px;background:var(--bg);color:var(--text);"
            + "font-family:Inter,-apple-system,Segoe UI,Roboto,sans-serif;font-size:14px;line-height:1.55}"
            + "h1{font-size:22px;margin:0 0 4px;color:var(--navy)}"
            + "h2{font-size:15px;text-transform:uppercase;letter-spacing:.08em;color:var(--muted);margin:0 0 14px}"
            + ".sub{color:var(--muted);font-size:13px}"
            + ".card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:22px;margin-bottom:20px}"
            + ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:16px}"
            + ".shot{border:1px solid var(--line);border-radius:8px;overflow:hidden;background:var(--card)}"
            + ".shot img{width:100%;display:block}"
            + ".shot span{display:block;padding:8px 10px;font-size:12px;color:var(--muted);border-top:1px solid var(--line)}"
            + "table{width:100%;border-collapse:collapse}"
            + "th{text-align:left;font-size:11px;text-transform:uppercase;letter-spacing:.06em;color:var(--muted);"
            + "padding:8px 10px;border-bottom:1px solid var(--line)}"
            + "td{padding:9px 10px;border-bottom:1px solid var(--line)}"
            + "tr:last-child td{border-bottom:none}"
            + ".kv{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:14px}"
            + ".kv div{background:var(--bg);border-radius:8px;padding:12px 14px}"
            + ".kv b{display:block;font-size:19px;color:var(--navy);font-weight:600}"
            + ".kv em{font-style:normal;font-size:11px;text-transform:uppercase;letter-spacing:.06em;color:var(--muted)}"
            + ".pill{display:inline-block;padding:2px 10px;border-radius:999px;font-size:12px;font-weight:600}"
            + ".good{background:#E4F4EA;color:var(--good)}"
            + ".warn{background:#FDF0DC;color:var(--warn)}"
            + ".bad{background:#FBE4E2;color:var(--bad)}"
            + ".bar{height:6px;border-radius:3px;background:var(--line);position:relative;min-width:70px}"
            + ".bar i{position:absolute;left:0;top:0;bottom:0;border-radius:3px}"
            + ".none{color:var(--muted);font-style:italic}"
            + "</style>";
    }

    private String header(PipelineContext ctx, PipelineResult result) {
        float conf = result.getOverallConfidence();
        return "<div class=\"card\"><h1>Scan Report</h1>"
            + "<div class=\"sub\">Session " + esc(ctx.getSessionId())
            + " &middot; Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            + " &middot; User " + esc(ctx.getUserId()) + "</div>"
            + "<div style=\"margin-top:14px\">" + pill(result.isSuccess() ? "SUCCESS" : "FAILED",
                result.isSuccess() ? "good" : "bad")
            + " " + pill("Confidence " + pct(conf), cls(conf))
            + " " + pill("Reference " + esc(ctx.getReferenceObjectType()), "good") + "</div></div>";
    }

    private String[][] folderImages(PipelineContext ctx) {
        int n = ctx.getFoodItems() == null ? 0 : ctx.getFoodItems().size();
        String[][] base = new String[4 + n * 3][2];
        base[0] = new String[]{"top_input.png", "Top view as received"};
        base[1] = new String[]{"top_boxes.png", "Detected boxes and plate circle"};
        base[2] = new String[]{"side_input.png", "Side view as received"};
        base[3] = new String[]{"side_boxes.png", "Side boxes"};
        for (int i = 0; i < n; i++) {
            String name = ctx.getFoodItems().get(i).getName();
            base[4 + i * 3] = new String[]{"top_" + i + "_raw.png", name + " raw mask"};
            base[5 + i * 3] = new String[]{"top_" + i + "_final.png", name + " after plate clip"};
            base[6 + i * 3] = new String[]{"side_" + i + ".png", name + " side mask"};
        }
        return base;
    }

    private String gallery(String[][] images) {
        StringBuilder sb = new StringBuilder("<div class=\"card\"><h2>Pipeline Imagery</h2><div class=\"grid\">");
        for (String[] img : images) {
            sb.append("<div class=\"shot\"><img src=\"").append(img[0])
              .append("\" onerror=\"this.parentNode.style.display='none'\"><span>")
              .append(esc(img[1])).append("</span></div>");
        }
        return sb.append("</div></div>").toString();
    }

    private String calibrationBlock(PipelineContext ctx) {
        StringBuilder sb = new StringBuilder("<div class=\"card\"><h2>Calibration &amp; Plate</h2><div class=\"kv\">");
        sb.append(kv("Top ratio", fmt(ctx.getPixelToCmRatioTop(), 5), "cm per pixel"));
        sb.append(kv("Side ratio", fmt(ctx.getPixelToCmRatioSide(), 5), "cm per pixel"));
        float[] circle = ctx.getPlateCircleTopPx();
        boolean hasCircle = circle != null && circle.length == 3 && circle[2] > 0;
        sb.append(kv("Plate circle", hasCircle ? "r=" + (int) circle[2] + "px" : "not measured", "pixels"));
        if (ctx.getPlateGeometry() != null) {
            sb.append(kv("Inner diameter", fmt(ctx.getPlateGeometry().getInnerDiameterCm(), 1), "cm"));
            sb.append(kv("Inner depth", fmt(ctx.getPlateGeometry().getInnerDepthCm(), 1), "cm"));
            sb.append(kv("Container", esc(ctx.getPlateGeometry().getContainerType()), "type"));
        }
        if (ctx.getMealTotals() != null) {
            sb.append(kv("Total weight", fmt(ctx.getMealTotals().getTotalWeightG(), 1), "grams"));
            sb.append(kv("Net carbs", fmt(ctx.getMealTotals().getTotalNetCarbsG(), 1), "grams"));
        }
        return sb.append("</div></div>").toString();
    }

    private String stagesBlock(PipelineContext ctx) {
        StringBuilder sb = new StringBuilder("<div class=\"card\"><h2>Stages</h2><table>"
            + "<tr><th>Stage</th><th>Confidence</th><th></th><th>Duration</th></tr>");
        Map<String, Float> conf = ctx.getStepConfidences();
        Map<String, Long> times = ctx.getStepTimingsMs();
        if (conf == null || conf.isEmpty()) {
            sb.append("<tr><td colspan=\"4\" class=\"none\">No stage data recorded</td></tr>");
        } else {
            for (Map.Entry<String, Float> e : conf.entrySet()) {
                Long ms = times == null ? null : times.get(e.getKey());
                sb.append("<tr><td>").append(esc(e.getKey())).append("</td><td>")
                  .append(pill(pct(e.getValue()), cls(e.getValue()))).append("</td><td>")
                  .append(bar(e.getValue())).append("</td><td>")
                  .append(ms == null ? "&mdash;" : ms + " ms").append("</td></tr>");
            }
        }
        return sb.append("</table></div>").toString();
    }

    private String itemsBlock(PipelineContext ctx) {
        StringBuilder sb = new StringBuilder("<div class=\"card\"><h2>Food Items</h2><table>"
            + "<tr><th>Item</th><th>Area</th><th>Height</th><th>Volume</th><th>Weight</th>"
            + "<th>Net carbs</th><th>Mask px</th><th>SAM score</th></tr>");
        if (ctx.getFoodItems() == null || ctx.getFoodItems().isEmpty()) {
            sb.append("<tr><td colspan=\"8\" class=\"none\">No items detected</td></tr>");
        } else {
            for (PipelineFoodItem it : ctx.getFoodItems()) {
                sb.append("<tr><td><b>").append(esc(it.getName())).append("</b></td><td>")
                  .append(fmt(it.getAreaCm2(), 1)).append(" cm&sup2;</td><td>")
                  .append(fmt(it.getEffectiveHeightCm(), 2)).append(" cm</td><td>")
                  .append(fmt(it.getVolumeCm3(), 1)).append(" cm&sup3;</td><td>")
                  .append(fmt(it.getWeightG(), 1)).append(" g</td><td>")
                  .append(fmt(it.getNetCarbsG(), 1)).append(" g</td><td>")
                  .append(it.getMaskPixelCount()).append("</td><td>")
                  .append(fmt(it.getSamMaskScore(), 3)).append("</td></tr>");
            }
        }
        return sb.append("</table></div>").toString();
    }

    private String warningsBlock(PipelineResult result) {
        StringBuilder sb = new StringBuilder("<div class=\"card\"><h2>Warnings</h2><table>"
            + "<tr><th>Severity</th><th>Stage</th><th>Code</th><th>Message</th></tr>");
        if (result.getWarnings() == null || result.getWarnings().isEmpty()) {
            sb.append("<tr><td colspan=\"4\" class=\"none\">No warnings raised</td></tr>");
        } else {
            for (PipelineWarning w : result.getWarnings()) {
                String sev = String.valueOf(w.getSeverity());
                String c = sev.toUpperCase().contains("HIGH") ? "bad"
                        : sev.toUpperCase().contains("MEDIUM") ? "warn" : "good";
                sb.append("<tr><td>").append(pill(esc(sev), c)).append("</td><td>")
                  .append(esc(w.getSourceStage())).append("</td><td>")
                  .append(esc(w.getCode())).append("</td><td>")
                  .append(esc(w.getMessage())).append("</td></tr>");
            }
        }
        return sb.append("</table></div>").toString();
    }

    private String kv(String label, String value, String unit) {
        return "<div><em>" + esc(label) + "</em><b>" + value + "</b><em>" + esc(unit) + "</em></div>";
    }

    private String pill(String text, String cssClass) {
        return "<span class=\"pill " + cssClass + "\">" + text + "</span>";
    }

    private String bar(Float value) {
        float v = value == null ? 0f : Math.max(0f, Math.min(1f, value));
        String color = v >= CONFIDENCE_GOOD ? "#1B7F4B" : v >= CONFIDENCE_WEAK ? "#B26A00" : "#B3261E";
        return "<div class=\"bar\"><i style=\"width:" + (int) (v * 100) + "%;background:" + color + "\"></i></div>";
    }

    private String cls(Float value) {
        if (value == null) return "warn";
        return value >= CONFIDENCE_GOOD ? "good" : value >= CONFIDENCE_WEAK ? "warn" : "bad";
    }

    private String pct(Float value) {
        return value == null ? "n/a" : Math.round(value * 100) + "%";
    }

    private String fmt(Number value, int decimals) {
        if (value == null) return "&mdash;";
        return String.format("%." + decimals + "f", value.doubleValue());
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "unassigned";
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private String esc(String value) {
        if (value == null) return "&mdash;";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}