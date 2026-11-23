package com.processing_backend.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.processing_backend.common.response.ApiResponse;
import com.processing_backend.config.AppProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

	private final AppProperties appProperties;

	public HealthController(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	@GetMapping("/health")
	public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("service", "processing_backend");
		data.put("status", "UP");
		data.put("defaultEmbeddingProvider", appProperties.getEmbedding().getDefaultProvider());
		data.put("embeddingDimensions", appProperties.getEmbedding().getDimensions());
		data.put("s3Bucket", appProperties.getAws().getS3Bucket());
		data.put("awsRegion", appProperties.getAws().getRegion());
		return ResponseEntity.ok(ApiResponse.ok("Processing service is up.", data));
	}
}
