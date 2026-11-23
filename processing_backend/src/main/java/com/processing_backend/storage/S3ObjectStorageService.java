package com.processing_backend.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
public class S3ObjectStorageService {

	private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageService.class);

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final AppProperties appProperties;

	public S3ObjectStorageService(S3Client s3Client, S3Presigner s3Presigner, AppProperties appProperties) {
		this.s3Client = s3Client;
		this.s3Presigner = s3Presigner;
		this.appProperties = appProperties;
	}

	public String buildObjectKey(UUID ownerUserId, UUID documentId) {
		return "documents/" + ownerUserId + "/" + documentId + "/v1.pdf";
	}

	public void putOriginalPdf(Path localPdf, String objectKey, String checksumSha256, long expectedSize) {
		if (localPdf == null || !Files.isRegularFile(localPdf)) {
			throw new BusinessException("Local original PDF is missing for S3 upload.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		try {
			long size = Files.size(localPdf);
			if (size != expectedSize) {
				throw new BusinessException(
						"Local PDF size does not match stored metadata.",
						HttpStatus.INTERNAL_SERVER_ERROR
				);
			}
			String bucket = appProperties.getAws().getS3Bucket();
			PutObjectRequest request = PutObjectRequest.builder()
					.bucket(bucket)
					.key(objectKey)
					.contentType("application/pdf")
					.contentLength(size)
					.serverSideEncryption(ServerSideEncryption.AES256)
					.metadata(java.util.Map.of("checksum-sha256", checksumSha256))
					.build();

			s3Client.putObject(request, RequestBody.fromFile(localPdf));
			log.info("Uploaded original PDF to S3 bucket={} key={} bytes={}", bucket, objectKey, size);
		}
		catch (BusinessException ex) {
			throw ex;
		}
		catch (IOException ex) {
			throw new BusinessException("Unable to read local PDF for S3 upload.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		catch (RuntimeException ex) {
			Throwable root = ex;
			while (root.getCause() != null && root.getCause() != root) {
				root = root.getCause();
			}
			log.warn(
					"S3 putObject failed type={} rootType={} rootMessage={}",
					ex.getClass().getSimpleName(),
					root.getClass().getSimpleName(),
					root.getMessage()
			);
			throw new BusinessException("Failed to upload original PDF to S3.", HttpStatus.BAD_GATEWAY);
		}
	}

	/**
	 * Deletes the original PDF object. Failures are surfaced so callers can retry cleanup.
	 */
	public void deleteOriginalPdf(String objectKey) {
		if (objectKey == null || objectKey.isBlank()) {
			return;
		}
		try {
			String bucket = appProperties.getAws().getS3Bucket();
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
			log.info("Deleted original PDF from S3 bucket={} key={}", bucket, objectKey);
		}
		catch (RuntimeException ex) {
			log.warn("S3 deleteObject failed type={} key={}", ex.getClass().getSimpleName(), objectKey);
			throw new BusinessException("Failed to delete original PDF from S3.", HttpStatus.BAD_GATEWAY);
		}
	}

	public String createDownloadUrl(String objectKey) {
		String bucket = appProperties.getAws().getS3Bucket();
		int expiry = Math.max(30, appProperties.getAws().getDownloadUrlExpirySeconds());
		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(bucket)
				.key(objectKey)
				.responseContentType("application/pdf")
				.build();
		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
				.signatureDuration(Duration.ofSeconds(expiry))
				.getObjectRequest(getObjectRequest)
				.build();
		PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
		return presigned.url().toString();
	}

	public String bucket() {
		return appProperties.getAws().getS3Bucket();
	}

	public int downloadExpirySeconds() {
		return Math.max(30, appProperties.getAws().getDownloadUrlExpirySeconds());
	}
}
