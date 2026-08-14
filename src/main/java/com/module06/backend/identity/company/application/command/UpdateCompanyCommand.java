package com.module06.backend.identity.company.application.command;

/** §4-3. 필드별로 null 이면 "값을 안 바꾼다"는 뜻이다 — 부분 수정. */
public record UpdateCompanyCommand(Long companyId, String name, String registrationNo,
                                    String representativeName, String address,
                                    Double latitude, Double longitude, String mainPhone) {
}
