package com.module06.backend.notice.application.usecase;

import com.module06.backend.notice.application.query.GetNoticeDetailQuery;
import com.module06.backend.notice.application.result.NoticeDetailResult;

/* NOTI-02 공지 상세 조회를 프레젠테이션 계층에 공개하는 인바운드 Port다. */
public interface GetNoticeDetailUseCase {

    /* 인증 회사에서 삭제되지 않은 공지 한 건의 상세 정보를 반환한다. */
    NoticeDetailResult getNotice(GetNoticeDetailQuery query);
}
