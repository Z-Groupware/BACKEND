package com.module06.backend.notice.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.module06.backend.notice.application.command.UpdateNoticeCommand;

/* NOTI-04 공지 수정 요청으로 제목과 본문 전체의 기본 형식을 검증한다. */
public record UpdateNoticeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content
) {

    /* 인증 원본·경로 식별자와 요청 본문을 공지 수정 Command로 변환한다. */
    public UpdateNoticeCommand toCommand(Long companyId, Long noticeId, Long memberId, String role) {
        /* 회사·수정자·권한은 조작 가능한 본문 대신 인증 principal 값만 사용한다. */
        return new UpdateNoticeCommand(companyId, noticeId, memberId, role, title, content);
    }
}
