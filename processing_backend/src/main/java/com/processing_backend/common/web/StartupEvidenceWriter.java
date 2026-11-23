package com.processing_backend.common.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.processing_backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Writes a small JSON evidence file after successful start (useful when HTTP
 * from the agent sandbox cannot reach the process network namespace).
 */
@Component
public class StartupEvidenceWriter implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(StartupEvidenceWriter.class);

	private final AppProperties appProperties;

	public StartupEvidenceWriter(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		String json = """
				{
				  "service": "processing_backend",
				  "status": "UP",
				  "s3Bucket": "%s",
				  "awsRegion": "%s",
				  "defaultEmbeddingProvider": "%s",
				  "embeddingDimensions": %d
				}
				""".formatted(
				appProperties.getAws().getS3Bucket(),
				appProperties.getAws().getRegion(),
				appProperties.getEmbedding().getDefaultProvider(),
				appProperties.getEmbedding().getDimensions());

		Path out = Path.of("tmp", "startup-evidence.json");
		Files.createDirectories(out.getParent());
		Files.writeString(out, json, StandardCharsets.UTF_8);
		log.info("Wrote startup evidence to {}", out.toAbsolutePath());
	}
}
