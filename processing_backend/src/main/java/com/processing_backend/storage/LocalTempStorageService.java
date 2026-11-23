package com.processing_backend.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LocalTempStorageService {

	private final Path root;

	public LocalTempStorageService(AppProperties appProperties) {
		this.root = Path.of(appProperties.getStorage().getLocalTempDir()).toAbsolutePath().normalize();
	}

	public Path store(UUID ownerUserId, UUID documentId, byte[] bytes) {
		try {
			Path ownerDir = root.resolve(ownerUserId.toString()).normalize();
			if (!ownerDir.startsWith(root)) {
				throw new BusinessException("Invalid storage path.", HttpStatus.INTERNAL_SERVER_ERROR);
			}
			Files.createDirectories(ownerDir);
			Path target = ownerDir.resolve(documentId + ".pdf").normalize();
			if (!target.startsWith(ownerDir)) {
				throw new BusinessException("Invalid storage path.", HttpStatus.INTERNAL_SERVER_ERROR);
			}
			Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			return target;
		}
		catch (BusinessException ex) {
			throw ex;
		}
		catch (IOException ex) {
			throw new BusinessException("Unable to store uploaded PDF temporarily.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ignored) {
			// best-effort cleanup
		}
	}
}
