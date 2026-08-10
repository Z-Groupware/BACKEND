package com.module06.backend.notice.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.module06.backend.notice.application.command.CreateNoticeCommand;

/* NOTI-03 공지 작성 요청 본문으로 제목과 내용의 기본 형식을 검증한다. */
public record CreateNoticeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content
) {

    /* 인증 principal 정보와 요청 본문을 공지 작성 Command로 변환한다. */
    public CreateNoticeCommand toCommand(Long companyId, Long memberId, String role) {
        /* 회사·작성자·권한은 사용자가 조작할 수 없는 인증 principal 값만 사용한다. */
        return new CreateNoticeCommand(companyId, memberId, role, title, content);
    }
}
