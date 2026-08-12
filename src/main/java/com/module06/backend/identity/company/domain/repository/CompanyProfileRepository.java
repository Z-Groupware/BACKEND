package com.module06.backend.identity.company.domain.repository;

import java.time.LocalDateTime;

/**
 * 기업 설정(§4-2·4-3)·온보딩 커밋(§4-1)이 함께 쓰는 쓰기 경로. {@link CompanyRepository}는
 * 읽기 전용을 유지하려는 기존 관례를 따라 쓰기를 이 인터페이스로 분리한다.
 */
public interface CompanyProfileRepository {

    /** 사업자번호 중복 확인(§4-3). 자기 자신은 제외한다 — 값을 안 바꾼 PATCH도 통과해야 한다. */
    boolean existsByRegistrationNoAndIdNot(String registrationNo, Long id);

    /** §4-3. name 은 필수, 나머지는 호출자가 기존 값과 병합해 넘긴다(부분 수정은 서비스 책임). */
    void updateProfile(Long id, String name, String registrationNo, String representativeName,
                        String address, Double latitude, Double longitude, String mainPhone);

    /** §4-1 온보딩 커밋 마지막 단계. */
    void markOnboarded(Long id, LocalDateTime now);
}
