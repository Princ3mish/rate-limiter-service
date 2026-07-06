package com.project.rate_limiter.controller.dto;

import java.util.List;
import java.util.Map;

public class DemoRunResponse {
    public String algorithm;
    public Map<String, Object> summary;
    public List<Map<String, Object>> timeline;
}