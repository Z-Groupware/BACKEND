package com.module06.backend.identity.company.domain.model;

/**
 * 기업. 로그인 1단계에서 코드로 찾는 대상이다.
 *
 * <p>순수 POJO 다 — Spring·JPA 애너테이션을 붙이지 않는다. 이번 범위는 조회만 하므로 필드를 셋으로
 * 막았다. 기업 정보 API 에서 확장한다.
 */
public record Company(Long id, String code, String name) {
}
