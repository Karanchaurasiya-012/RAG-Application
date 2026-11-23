package com.auth_backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

	private final boolean success;
	private final String message;
	private final T data;

	private ApiResponse(boolean success, String message, T data) {
		this.success = success;
		this.message = message;
		this.data = data;
	}

	public static <T> ApiResponse<T> ok(String message, T data) {
		return new ApiResponse<>(true, message, data);
	}

	public static ApiResponse<Void> ok(String message) {
		return new ApiResponse<>(true, message, null);
	}

	public static ApiResponse<Void> fail(String message) {
		return new ApiResponse<>(false, message, null);
	}

	public static <T> ApiResponse<T> fail(String message, T data) {
		return new ApiResponse<>(false, message, data);
	}

	public boolean isSuccess() {
		return success;
	}

	public String getMessage() {
		return message;
	}

	public T getData() {
		return data;
	}
}
