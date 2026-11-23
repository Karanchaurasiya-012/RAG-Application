package com.processing_backend.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.common.exception.GlobalExceptionHandler;
import com.processing_backend.document.controller.DocumentController;
import com.processing_backend.document.dto.DocumentDetailsResponse;
import com.processing_backend.document.dto.PiiDecisionResponse;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.entity.PiiDecision;
import com.processing_backend.document.service.DocumentDeleteService;
import com.processing_backend.document.service.DocumentDownloadService;
import com.processing_backend.document.service.DocumentPiiDecisionService;
import com.processing_backend.document.service.DocumentQueryService;
import com.processing_backend.document.service.DocumentUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DocumentController.class)
@Import({
		CurrentUser.class,
		GlobalExceptionHandler.class,
		DocumentSecurityWebMvcTest.TestSecurityConfig.class
})
class DocumentSecurityWebMvcTest {

	private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
	private static final UUID DOC_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DocumentUploadService documentUploadService;
	@MockitoBean
	private DocumentPiiDecisionService documentPiiDecisionService;
	@MockitoBean
	private DocumentDownloadService documentDownloadService;
	@MockitoBean
	private DocumentQueryService documentQueryService;
	@MockitoBean
	private DocumentDeleteService documentDeleteService;

	@Test
	void missingJwtReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/documents/{id}", DOC_ID))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void invalidBearerTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/documents/{id}", DOC_ID)
						.header("Authorization", "Bearer not-a-real-jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void ownerCanReadOwnDocument() throws Exception {
		when(documentQueryService.getDetails(USER_A, DOC_ID)).thenReturn(sampleDetails());

		mockMvc.perform(get("/api/v1/documents/{id}", DOC_ID)
						.with(jwt().jwt(j -> j.subject(USER_A.toString()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(documentQueryService).getDetails(USER_A, DOC_ID);
	}

	@Test
	void userBCannotAccessUserADocument() throws Exception {
		when(documentQueryService.getDetails(USER_B, DOC_ID))
				.thenThrow(new BusinessException("Document not found.", HttpStatus.NOT_FOUND));

		mockMvc.perform(get("/api/v1/documents/{id}", DOC_ID)
						.with(jwt().jwt(j -> j.subject(USER_B.toString()))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void userBCannotDeleteUserADocument() throws Exception {
		when(documentDeleteService.delete(USER_B, DOC_ID))
				.thenThrow(new BusinessException("Document not found.", HttpStatus.NOT_FOUND));

		mockMvc.perform(delete("/api/v1/documents/{id}", DOC_ID)
						.with(jwt().jwt(j -> j.subject(USER_B.toString()))))
				.andExpect(status().isNotFound());
	}

	@Test
	void userBCannotSubmitPiiDecisionForUserADocument() throws Exception {
		when(documentPiiDecisionService.submitDecision(eq(USER_B), eq(DOC_ID), any()))
				.thenThrow(new BusinessException("Document not found.", HttpStatus.NOT_FOUND));

		mockMvc.perform(post("/api/v1/documents/{id}/pii-decision", DOC_ID)
						.with(jwt().jwt(j -> j.subject(USER_B.toString())))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"decision\":\"MASK\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void authenticatedPiiDecisionUsesJwtSubjectNotBodyUserId() throws Exception {
		when(documentPiiDecisionService.submitDecision(USER_A, DOC_ID, PiiDecision.MASK))
				.thenReturn(new PiiDecisionResponse(
						DOC_ID,
						DocumentStatus.PREPARING_TEXT,
						PiiDecision.MASK,
						true,
						"ok"
				));

		mockMvc.perform(post("/api/v1/documents/{id}/pii-decision", DOC_ID)
						.with(jwt().jwt(j -> j.subject(USER_A.toString())))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"decision\":\"MASK\",\"ownerUserId\":\"" + USER_B + "\"}"))
				.andExpect(status().isAccepted());

		verify(documentPiiDecisionService).submitDecision(USER_A, DOC_ID, PiiDecision.MASK);
	}

	private static DocumentDetailsResponse sampleDetails() {
		return new DocumentDetailsResponse(
				DOC_ID,
				DocumentStatus.COMPLETED,
				"a.pdf",
				"application/pdf",
				10L,
				"abc",
				1,
				1,
				false,
				null,
				false,
				List.of(),
				List.of(),
				true,
				true,
				"ollama",
				"bge-m3:latest",
				1024,
				java.time.Instant.parse("2026-01-01T00:00:00Z"),
				java.time.Instant.parse("2026-01-01T00:00:00Z")
		);
	}

	static class TestSecurityConfig {
		@Bean
		SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
			BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();
			http
					.csrf(csrf -> csrf.disable())
					.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
					.authorizeHttpRequests(auth -> auth
							.requestMatchers("/api/v1/health").permitAll()
							.anyRequest().authenticated()
					)
					.oauth2ResourceServer(oauth2 -> oauth2
							.jwt(Customizer.withDefaults())
							.authenticationEntryPoint(entryPoint)
					)
					.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));
			return http.build();
		}

		@Bean
		JwtDecoder jwtDecoder() {
			// Token parsing for Authorization header failures; jwt() post-processor bypasses this.
			return token -> {
				throw new BadJwtException("invalid token");
			};
		}
	}
}
