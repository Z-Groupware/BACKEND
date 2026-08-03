package com.module06.backend.global.response;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final int httpStatus;
    private final String message;
    private final T data;

    private ApiResponse(HttpStatus httpStatus, String message, T data) {
        this.httpStatus = httpStatus.value();
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(HttpStatus.OK, message, data);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(HttpStatus.CREATED, message, data);
    }

    public static ApiResponse<Void> successWithoutData(String message) {
        return new ApiResponse<>(HttpStatus.OK, message, null);
    }
}
