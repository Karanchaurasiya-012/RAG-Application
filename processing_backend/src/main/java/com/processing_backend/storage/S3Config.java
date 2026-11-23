package com.processing_backend.storage;

import com.processing_backend.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

	@Bean(destroyMethod = "close")
	S3Client s3Client(AppProperties appProperties) {
		return S3Client.builder()
				.region(Region.of(appProperties.getAws().getRegion()))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}

	@Bean(destroyMethod = "close")
	S3Presigner s3Presigner(AppProperties appProperties) {
		return S3Presigner.builder()
				.region(Region.of(appProperties.getAws().getRegion()))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}
}
