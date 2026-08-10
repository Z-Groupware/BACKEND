package com.module06.backend.notice.domain.repository;

import com.module06.backend.notice.domain.model.Notice;

/* NOTI-03 공지 작성에 필요한 도메인 저장소 계약이다. */
public interface NoticeCommandRepository {

    /* 신규 공지를 저장하고 데이터베이스 식별자가 반영된 도메인 모델을 반환한다. */
    Notice save(Notice notice);
}
