package com.processing_backend.document.repository;

import java.util.List;
import java.util.UUID;

import com.processing_backend.document.entity.PiiFinding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PiiFindingRepository extends JpaRepository<PiiFinding, UUID> {

	List<PiiFinding> findByDocumentId(UUID documentId);

	void deleteByDocumentId(UUID documentId);
}
