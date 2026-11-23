package com.processing_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

	private final Jwt jwt = new Jwt();
	private final Aws aws = new Aws();
	private final Embedding embedding = new Embedding();
	private final Pdf pdf = new Pdf();
	private final Chunking chunking = new Chunking();
	private final Storage storage = new Storage();
	private final Jobs jobs = new Jobs();
	private final Cors cors = new Cors();

	public Jwt getJwt() {
		return jwt;
	}

	public Aws getAws() {
		return aws;
	}

	public Embedding getEmbedding() {
		return embedding;
	}

	public Pdf getPdf() {
		return pdf;
	}

	public Chunking getChunking() {
		return chunking;
	}

	public Storage getStorage() {
		return storage;
	}

	public Jobs getJobs() {
		return jobs;
	}

	public Cors getCors() {
		return cors;
	}

	public static class Jwt {
		private String issuer;
		private String audience;
		private String publicKeyPath;

		public String getIssuer() {
			return issuer;
		}

		public void setIssuer(String issuer) {
			this.issuer = issuer;
		}

		public String getAudience() {
			return audience;
		}

		public void setAudience(String audience) {
			this.audience = audience;
		}

		public String getPublicKeyPath() {
			return publicKeyPath;
		}

		public void setPublicKeyPath(String publicKeyPath) {
			this.publicKeyPath = publicKeyPath;
		}
	}

	public static class Aws {
		private String region;
		private String s3Bucket;
		private int downloadUrlExpirySeconds = 300;

		public String getRegion() {
			return region;
		}

		public void setRegion(String region) {
			this.region = region;
		}

		public String getS3Bucket() {
			return s3Bucket;
		}

		public void setS3Bucket(String s3Bucket) {
			this.s3Bucket = s3Bucket;
		}

		public int getDownloadUrlExpirySeconds() {
			return downloadUrlExpirySeconds;
		}

		public void setDownloadUrlExpirySeconds(int downloadUrlExpirySeconds) {
			this.downloadUrlExpirySeconds = downloadUrlExpirySeconds;
		}
	}

	public static class Embedding {
		private String defaultProvider;
		private int dimensions;
		private final Ollama ollama = new Ollama();
		private final Gemini gemini = new Gemini();

		public String getDefaultProvider() {
			return defaultProvider;
		}

		public void setDefaultProvider(String defaultProvider) {
			this.defaultProvider = defaultProvider;
		}

		public int getDimensions() {
			return dimensions;
		}

		public void setDimensions(int dimensions) {
			this.dimensions = dimensions;
		}

		public Ollama getOllama() {
			return ollama;
		}

		public Gemini getGemini() {
			return gemini;
		}

		public static class Ollama {
			private String baseUrl;
			private String model;

			public String getBaseUrl() {
				return baseUrl;
			}

			public void setBaseUrl(String baseUrl) {
				this.baseUrl = baseUrl;
			}

			public String getModel() {
				return model;
			}

			public void setModel(String model) {
				this.model = model;
			}
		}

		public static class Gemini {
			private String model;
			private String apiKey;

			public String getModel() {
				return model;
			}

			public void setModel(String model) {
				this.model = model;
			}

			public String getApiKey() {
				return apiKey;
			}

			public void setApiKey(String apiKey) {
				this.apiKey = apiKey;
			}
		}
	}

	public static class Pdf {
		private int maxSizeMb;
		private int maxPages;

		public int getMaxSizeMb() {
			return maxSizeMb;
		}

		public void setMaxSizeMb(int maxSizeMb) {
			this.maxSizeMb = maxSizeMb;
		}

		public int getMaxPages() {
			return maxPages;
		}

		public void setMaxPages(int maxPages) {
			this.maxPages = maxPages;
		}
	}

	public static class Chunking {
		private int chunkSize;
		private int overlap;

		public int getChunkSize() {
			return chunkSize;
		}

		public void setChunkSize(int chunkSize) {
			this.chunkSize = chunkSize;
		}

		public int getOverlap() {
			return overlap;
		}

		public void setOverlap(int overlap) {
			this.overlap = overlap;
		}
	}

	public static class Storage {
		private String localTempDir = "./tmp/uploads";

		public String getLocalTempDir() {
			return localTempDir;
		}

		public void setLocalTempDir(String localTempDir) {
			this.localTempDir = localTempDir;
		}
	}

	public static class Jobs {
		private int concurrency = 2;
		private int queueCapacity = 50;
		private int maxAttempts = 3;
		private long retryDelayMs = 5000;
		private long pollIntervalMs = 1000;
		private long staleRunningSeconds = 900;
		private long jobExpirySeconds = 86400;

		public int getConcurrency() {
			return concurrency;
		}

		public void setConcurrency(int concurrency) {
			this.concurrency = concurrency;
		}

		public int getQueueCapacity() {
			return queueCapacity;
		}

		public void setQueueCapacity(int queueCapacity) {
			this.queueCapacity = queueCapacity;
		}

		public int getMaxAttempts() {
			return maxAttempts;
		}

		public void setMaxAttempts(int maxAttempts) {
			this.maxAttempts = maxAttempts;
		}

		public long getRetryDelayMs() {
			return retryDelayMs;
		}

		public void setRetryDelayMs(long retryDelayMs) {
			this.retryDelayMs = retryDelayMs;
		}

		public long getPollIntervalMs() {
			return pollIntervalMs;
		}

		public void setPollIntervalMs(long pollIntervalMs) {
			this.pollIntervalMs = pollIntervalMs;
		}

		public long getStaleRunningSeconds() {
			return staleRunningSeconds;
		}

		public void setStaleRunningSeconds(long staleRunningSeconds) {
			this.staleRunningSeconds = staleRunningSeconds;
		}

		public long getJobExpirySeconds() {
			return jobExpirySeconds;
		}

		public void setJobExpirySeconds(long jobExpirySeconds) {
			this.jobExpirySeconds = jobExpirySeconds;
		}
	}

	public static class Cors {
		private java.util.List<String> allowedOrigins = java.util.List.of("http://localhost:3000");

		public java.util.List<String> getAllowedOrigins() {
			return allowedOrigins;
		}

		public void setAllowedOrigins(java.util.List<String> allowedOrigins) {
			this.allowedOrigins = allowedOrigins;
		}
	}
}
