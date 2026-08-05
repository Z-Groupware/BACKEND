package com.module06.backend.identity.company.application.port.out;

/**
 * 기업코드 조회의 IP 당 호출 제한.
 *
 * <p>이 API 는 토큰 없이 부를 수 있고 200/404 로 답하므로, 코드를 무작위로 넣어 회사 존재를 확인할
 * 수 있다. 초과 시 예외를 던진다.
 */
public interface LookupRateLimiter {

    void checkOrThrow(String clientIp);
}
