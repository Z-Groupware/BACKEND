package com.module06.backend.identity.company.domain.repository;

import java.time.LocalDateTime;

/**
 * 기업 설정(§4-2·4-3)·온보딩 커밋(§4-1)이 함께 쓰는 쓰기 경로. {@link CompanyRepository}는
 * 읽기 전용을 유지하려는 기존 관례를 따라 쓰기를 이 인터페이스로 분리한다.
 */
public interface CompanyProfileRepository {

    /** 사업자번호 중복 확인(§4-3). 자기 자신은 제외한다 — 값을 안 바꾼 PATCH도 통과해야 한다. */
    boolean existsByRegistrationNoAndIdNot(String registrationNo, Long id);

    /**
     * §4-3 부분 수정. {@code null} 인 필드는 현재 값을 그대로 두고, 값이 온 필드만 갱신한다 —
     * 호출자가 기존 값을 읽어 병합해 넘기지 않는다.
     *
     * <p>병합을 구현체(엔티티)에 맡기는 이유는 lost update 다. 호출자가 미리 읽은 스냅샷과
     * 병합하면, 그 사이 다른 PATCH 가 커밋한 변경을 자기 스냅샷 값으로 되돌린다
     * (구현·근거는 {@code CompanyJpaEntity.updateProfile} 의 주석).
     */
    void updateProfile(Long id, String name, String registrationNo, String representativeName,
                        String address, Double latitude, Double longitude, String mainPhone);

    /** §4-1 온보딩 커밋 마지막 단계. */
    void markOnboarded(Long id, LocalDateTime now);
}
