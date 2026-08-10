package com.module06.backend.notice.application.usecase;

import com.module06.backend.notice.application.command.DeleteNoticeCommand;

/* NOTI-05 공지 소프트 삭제 진입점을 프레젠테이션 계층에 제공하는 인바운드 Port다. */
public interface DeleteNoticeUseCase {

    /* 인증 회사의 활성 공지를 소프트 삭제한다. */
    void deleteNotice(DeleteNoticeCommand command);
}
