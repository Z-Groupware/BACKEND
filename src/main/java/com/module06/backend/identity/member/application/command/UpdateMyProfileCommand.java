package com.module06.backend.identity.member.application.command;

/** 마이페이지 셀프 프로필 수정. null 인 필드는 값을 바꾸지 않는다 — 부분 수정. */
public record UpdateMyProfileCommand(Long memberId, Long companyId, Long teamId, Long positionId, String phone) {
}
