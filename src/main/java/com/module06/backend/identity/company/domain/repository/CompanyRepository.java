package com.module06.backend.identity.company.domain.repository;

import java.util.Optional;

import com.module06.backend.identity.company.domain.model.Company;

/**
 * 로그인 1단계가 쓰는 읽기 경로. 메서드를 하나로 유지한다 —
 * 쓰기는 {@link CompanyRegistrationRepository} 가 따로 맡는다.
 */
public interface CompanyRepository {

    Optional<Company> findByCode(String code);

    /** 계정 발급 메일에 실을 기업 코드 조회용(§5-1) — companyId 는 인증 토큰 클레임에 이미 있다. */
    Optional<Company> findById(Long id);

    /**
     * 계정 발급 동시성 직렬화용(§5-1). 같은 회사로 동시에 여러 발급 요청이 들어오면 좌석 상한·
     * 팀장 유일성 검사가 서로의 쓰기를 보지 못한 채 통과할 수 있다 — 이 회사 행에 비관적 쓰기 잠금을
     * 걸어, 트랜잭션이 끝날 때까지 같은 회사의 다른 발급을 대기시킨다(MemberIssuer 에서 검사 직전에
     * 호출). 반드시 활성 트랜잭션 안에서 불러야 한다.
     */
    void lockForUpdate(Long companyId);
}
