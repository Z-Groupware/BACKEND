package com.module06.backend.identity.company.domain.model;

import java.time.LocalDateTime;

/**
 * 기업 기본 정보 조회(§4-2)까지 겸하므로 프로필 필드를 함께 들고 있다.
 *
 * <p>{@code latitude}·{@code longitude} 는 주소와 짝이지만 별도 값 객체로 묶지 않는다 — 둘 중
 * 하나만 있는 상태(주소만 입력, 지도 미사용)가 정상이라 항상 함께 존재한다는 전제를 세울 수 없다.
 */
public record Company(Long id, String code, String name, String registrationNo,
                       String representativeName, String address, Double latitude, Double longitude,
                       String mainPhone, LocalDateTime onboardedAt) {
}
