package com.module06.backend.identity.company.domain.model;

import java.time.LocalDateTime;

/** 기업 기본 정보 조회(§4-2)까지 겸하므로 프로필 필드를 함께 들고 있다. */
public record Company(Long id, String code, String name, String registrationNo,
                       String representativeName, String address, String mainPhone,
                       LocalDateTime onboardedAt) {
}
