package com.module06.backend.notice.domain.repository;

import java.util.Optional;

import com.module06.backend.notice.domain.model.Notice;

/* NOTI-03·04 공지 작성과 수정에 필요한 도메인 저장소 계약이다. */
public interface NoticeCommandRepository {

    /* 인증 회사 범위에서 삭제되지 않은 수정 대상 공지를 조회한다. */
    Optional<Notice> findActiveNotice(Long companyId, Long noticeId);

    /* 신규 또는 수정 공지를 저장하고 데이터베이스 상태가 반영된 도메인 모델을 반환한다. */
    Notice save(Notice notice);
}
