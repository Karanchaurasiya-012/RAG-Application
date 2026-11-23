package com.processing_backend.document.controller;

import java.util.UUID;

import com.processing_backend.common.response.ApiResponse;
import com.processing_backend.document.dto.DocumentDeleteResponse;
import com.processing_backend.document.dto.DocumentDetailsResponse;
import com.processing_backend.document.dto.DocumentDownloadResponse;
import com.processing_backend.document.dto.DocumentListResponse;
import com.processing_backend.document.dto.DocumentStatusResponse;
import com.processing_backend.document.dto.DocumentUploadResponse;
import com.processing_backend.document.dto.PiiDecisionRequest;
import com.processing_backend.document.dto.PiiDecisionResponse;
import com.processing_backend.document.service.DocumentDeleteService;
import com.processing_backend.document.service.DocumentDownloadService;
import com.processing_backend.document.service.DocumentPiiDecisionService;
import com.processing_backend.document.service.DocumentQueryService;
import com.processing_backend.document.service.DocumentUploadService;
import com.processing_backend.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

	private final DocumentUploadService documentUploadService;
	private final DocumentPiiDecisionService documentPiiDecisionService;
	private final DocumentDownloadService documentDownloadService;
	private final DocumentQueryService documentQueryService;
	private final DocumentDeleteService documentDeleteService;
	private final CurrentUser currentUser;

	public DocumentController(
			DocumentUploadService documentUploadService,
			DocumentPiiDecisionService documentPiiDecisionService,
			DocumentDownloadService documentDownloadService,
			DocumentQueryService documentQueryService,
			DocumentDeleteService documentDeleteService,
			CurrentUser currentUser
	) {
		this.documentUploadService = documentUploadService;
		this.documentPiiDecisionService = documentPiiDecisionService;
		this.documentDownloadService = documentDownloadService;
		this.documentQueryService = documentQueryService;
		this.documentDeleteService = documentDeleteService;
		this.currentUser = currentUser;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<DocumentUploadResponse>> upload(
			@RequestPart("file") MultipartFile file,
			@RequestParam(value = "embeddingProvider", required = false) String embeddingProvider
	) {
		UUID ownerUserId = currentUser.requireUserId();
		DocumentUploadResponse data = documentUploadService.upload(ownerUserId, file, embeddingProvider);
		String message = data.duplicate()
				? "Duplicate PDF detected for this user. Returning existing document."
				: "PDF validated and accepted. Processing continues asynchronously.";
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(message, data));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<DocumentListResponse>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		UUID ownerUserId = currentUser.requireUserId();
		DocumentListResponse data = documentQueryService.list(ownerUserId, page, size);
		return ResponseEntity.ok(ApiResponse.ok("Documents listed.", data));
	}

	@GetMapping("/{documentId}")
	public ResponseEntity<ApiResponse<DocumentDetailsResponse>> details(@PathVariable UUID documentId) {
		UUID ownerUserId = currentUser.requireUserId();
		DocumentDetailsResponse data = documentQueryService.getDetails(ownerUserId, documentId);
		return ResponseEntity.ok(ApiResponse.ok("Document details.", data));
	}

	@GetMapping("/{documentId}/status")
	public ResponseEntity<ApiResponse<DocumentStatusResponse>> status(@PathVariable UUID documentId) {
		UUID ownerUserId = currentUser.requireUserId();
		DocumentStatusResponse data = documentQueryService.getStatus(ownerUserId, documentId);
		return ResponseEntity.ok(ApiResponse.ok("Document status.", data));
	}

	@PostMapping("/{documentId}/pii-decision")
	public ResponseEntity<ApiResponse<PiiDecisionResponse>> submitPiiDecision(
			@PathVariable UUID documentId,
			@Valid @RequestBody PiiDecisionRequest request
	) {
		UUID ownerUserId = currentUser.requireUserId();
		PiiDecisionResponse data = documentPiiDecisionService.submitDecision(
				ownerUserId,
				documentId,
				request.getDecision()
		);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(
				"PII decision recorded. The original PDF will remain unchanged; masking applies only to extracted text. Processing continues asynchronously.",
				data
		));
	}

	@GetMapping("/{documentId}/download")
	public ResponseEntity<ApiResponse<DocumentDownloadResponse>> download(@PathVariable UUID documentId) {
		UUID ownerUserId = currentUser.requireUserId();
		DocumentDownloadResponse data = documentDownloadService.createAuthorizedDownload(ownerUserId, documentId);
		return ResponseEntity.ok(ApiResponse.ok("Authorized download URL generated.", data));
	}

	@DeleteMapping("/{documentId}")
	public ResponseEntity<ApiResponse<DocumentDeleteResponse>> delete(@PathVariable UUID documentId) {
		UUID ownerUserId = currentUser.requireUserId();
		DocumentDeleteResponse data = documentDeleteService.delete(ownerUserId, documentId);
		return ResponseEntity.ok(ApiResponse.ok("Document deleted.", data));
	}
}
