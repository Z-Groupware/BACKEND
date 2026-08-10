package com.module06.backend.notice.application.usecase;

import com.module06.backend.notice.application.command.CreateNoticeCommand;
import com.module06.backend.notice.application.result.NoticeCreationResult;

/* NOTI-03 공지 작성을 프레젠테이션 계층에 공개하는 인바운드 Port다. */
public interface CreateNoticeUseCase {

    /* 인증된 OWNER·ADMIN의 회사에 검증된 공지를 작성한다. */
    NoticeCreationResult createNotice(CreateNoticeCommand command);
}
