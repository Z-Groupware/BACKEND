package com.module06.backend.notice.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

/*
 * NOTI-01 회사별 활성 공지 목록을 조회하는 도메인 저장소 계약이다.
 *
 * JPA 엔티티나 Spring Data 타입을 노출하지 않고 목록 화면에 필요한 최소 필드만 반환한다.
 */
public interface NoticeQueryRepository {

    /* 회사의 삭제되지 않은 공지를 최신 생성 순서로 조회한다. */
    List<NoticeListSnapshot> findActiveNoticesByCompanyId(Long companyId);

    /* 공지 목록 한 행에 필요한 식별자·제목·생성 시각을 담는 읽기 모델이다. */
    record NoticeListSnapshot(Long noticeId, String title, LocalDateTime createdAt) {
    }
}
