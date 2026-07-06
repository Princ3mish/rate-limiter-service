package com.project.rate_limiter.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.project.rate_limiter.controller.dto.DemoEvent;

public class ResponseTextHelper {

    public static Map<String, Object> buildSummary(List<DemoEvent> events, Map<String, Object> config) {
        long allowed = events.stream().filter(e -> e.status() == 200).count();
        long blocked = events.stream().filter(e -> e.status() == 429).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("allowed", allowed);
        summary.put("blocked", blocked);
        summary.put("config", config);
        return summary;
    }

    public static List<Map<String, Object>> formatTimeline(List<DemoEvent> events) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (DemoEvent event : events) {
            Map<String, Object> eventMap = new LinkedHashMap<>();
            if (event.comment() != null && event.comment().contains("interval")) {
                // Generates the MARKER checkpoint
                eventMap.put("comment", event.comment());
                eventMap.put("event", "MARKER");
            } else {
                eventMap.put("status", event.status());
                if (event.status() == 200) {
                    eventMap.put("remaining", event.remaining());
                } else {
                    eventMap.put("retryAfterMs", event.retryAfterMs());
                }
                if (event.comment() != null && !event.comment().isBlank()) {
                    eventMap.put("comment", event.comment());
                }
            }
            timeline.add(eventMap);
        }
        return timeline;
    }
}