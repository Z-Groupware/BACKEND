package com.module06.backend.identity.company.application.dto;

/**
 * 등록 결과. <b>비밀번호를 담지 않는다.</b>
 *
 * <p>응답에 실으면 브라우저 개발자도구·중간 프록시 로그에 평문이 그대로 남는다. 비밀번호가
 * 사용자에게 가는 경로는 메일 하나뿐이다.
 *
 * <p>{@code companyCode} 는 담는다 — 비밀번호와 달리 자격증명이 아니라 식별자이고, 메일이 늦게
 * 도착해도 화면에서 바로 안내할 수 있어야 한다.
 */
public record CompanyRegistrationResult(
        Long companyId,
        String companyCode,
        String ownerEmail
) {
}
