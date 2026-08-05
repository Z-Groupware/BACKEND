package com.module06.backend.global.response;

import org.springframework.http.HttpStatus;

import lombok.Getter;

// 전 도메인 공용 성공 응답 겉포장. 컨트롤러가 이걸 리턴하면 스프링이 JSON({httpStatus, message, data})으로 바꿔준다.
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

    // 200 OK 응답
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(HttpStatus.OK, message, data);
    }

    // 201 Created 응답
    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(HttpStatus.CREATED, message, data);
    }

    // 200 OK, data 없는 응답
    public static ApiResponse<Void> successWithoutData(String message) {
        return new ApiResponse<>(HttpStatus.OK, message, null);
    }

    // 202 Accepted 응답 (비동기로 접수만 됐을 때 — 예: CAP의 청크 업로드 완료 통보)
    public static <T> ApiResponse<T> accepted(String message, T data) {
        return new ApiResponse<>(HttpStatus.ACCEPTED, message, data);
    }
}
