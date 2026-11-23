package com.auth_backend.auth.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.auth_backend.auth.dto.request.ForgotPasswordRequest;
import com.auth_backend.auth.dto.request.LoginRequest;
import com.auth_backend.auth.dto.request.RegisterRequest;
import com.auth_backend.auth.dto.request.ResendVerificationRequest;
import com.auth_backend.auth.dto.request.ResetPasswordRequest;
import com.auth_backend.auth.dto.request.VerifyEmailRequest;
import com.auth_backend.auth.dto.response.AuthResponse;
import com.auth_backend.auth.dto.response.UserResponse;
import com.auth_backend.auth.mapper.UserMapper;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.service.EmailVerificationService;
import com.auth_backend.auth.service.LoginService;
import com.auth_backend.auth.service.PasswordResetService;
import com.auth_backend.auth.service.RegistrationService;
import com.auth_backend.auth.service.SessionService;
import com.auth_backend.common.exception.BusinessException;
import com.auth_backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final RegistrationService registrationService;
	private final EmailVerificationService emailVerificationService;
	private final LoginService loginService;
	private final SessionService sessionService;
	private final PasswordResetService passwordResetService;
	private final UserRepository userRepository;
	private final UserMapper userMapper;

	public AuthController(
			RegistrationService registrationService,
			EmailVerificationService emailVerificationService,
			LoginService loginService,
			SessionService sessionService,
			PasswordResetService passwordResetService,
			UserRepository userRepository,
			UserMapper userMapper
	) {
		this.registrationService = registrationService;
		this.emailVerificationService = emailVerificationService;
		this.loginService = loginService;
		this.sessionService = sessionService;
		this.passwordResetService = passwordResetService;
		this.userRepository = userRepository;
		this.userMapper = userMapper;
	}

	@GetMapping("/csrf")
	public ResponseEntity<ApiResponse<Map<String, String>>> csrf(CsrfToken csrfToken) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("headerName", csrfToken.getHeaderName());
		data.put("parameterName", csrfToken.getParameterName());
		data.put("token", csrfToken.getToken());
		return ResponseEntity.ok(ApiResponse.ok("CSRF token issued.", data));
	}

	@PostMapping("/register")
	public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
		registrationService.register(request);
		return ResponseEntity.ok(ApiResponse.ok(RegistrationService.GENERIC_REGISTER_MESSAGE));
	}

	@PostMapping("/verify-email")
	public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		emailVerificationService.verifyEmail(request.getToken());
		return ResponseEntity.ok(ApiResponse.ok(EmailVerificationService.SUCCESS_MESSAGE));
	}

	@PostMapping("/resend-verification")
	public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
		emailVerificationService.resendVerification(request);
		return ResponseEntity.ok(ApiResponse.ok(EmailVerificationService.GENERIC_RESEND_MESSAGE));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AuthResponse>> login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse
	) {
		AuthResponse authResponse = loginService.login(request, httpRequest, httpResponse);
		return ResponseEntity.ok(ApiResponse.ok(LoginService.SUCCESS_MESSAGE, authResponse));
	}

	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request, HttpServletResponse response) {
		sessionService.refresh(request, response);
		return ResponseEntity.ok(ApiResponse.ok(SessionService.REFRESH_SUCCESS_MESSAGE));
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
		sessionService.logout(request, response);
		return ResponseEntity.ok(ApiResponse.ok(SessionService.LOGOUT_SUCCESS_MESSAGE));
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		passwordResetService.forgotPassword(request);
		return ResponseEntity.ok(ApiResponse.ok(PasswordResetService.GENERIC_FORGOT_MESSAGE));
	}

	@PostMapping("/reset-password")
	public ResponseEntity<ApiResponse<Void>> resetPassword(
			@Valid @RequestBody ResetPasswordRequest request,
			HttpServletResponse response
	) {
		passwordResetService.resetPassword(request, response);
		return ResponseEntity.ok(ApiResponse.ok(PasswordResetService.RESET_SUCCESS_MESSAGE));
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal Jwt jwt) {
		UUID userId = UUID.fromString(jwt.getSubject());
		UserResponse user = userRepository.findWithRolesById(userId)
				.map(userMapper::toResponse)
				.orElseThrow(() -> new BusinessException("User not found.", HttpStatus.UNAUTHORIZED));
		return ResponseEntity.ok(ApiResponse.ok("User fetched successfully.", user));
	}
}
