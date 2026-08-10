package com.module06.backend.notice.application.usecase;

import com.module06.backend.notice.application.command.UpdateNoticeCommand;
import com.module06.backend.notice.application.result.NoticeUpdateResult;

/* NOTI-04 공지 수정 진입점을 프레젠테이션 계층에 제공하는 인바운드 Port다. */
public interface UpdateNoticeUseCase {

    /* 인증 회사의 활성 공지를 전체 수정하고 최종 공지 상태를 반환한다. */
    NoticeUpdateResult updateNotice(UpdateNoticeCommand command);
}
