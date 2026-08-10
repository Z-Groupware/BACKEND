package com.module06.backend.notice.application.usecase;

import com.module06.backend.notice.application.query.GetNoticeListQuery;
import com.module06.backend.notice.application.result.NoticeListResult;

/* NOTI-01 공지 목록 조회를 프레젠테이션 계층에 공개하는 인바운드 Port다. */
public interface GetNoticeListUseCase {

    /* 인증 회사의 활성 공지 목록을 최신순으로 반환한다. */
    NoticeListResult getNotices(GetNoticeListQuery query);
}
