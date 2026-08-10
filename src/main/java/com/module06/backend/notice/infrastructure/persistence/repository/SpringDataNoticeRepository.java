package com.module06.backend.notice.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.notice.infrastructure.persistence.entity.NoticeJpaEntity;

/* notice 테이블의 파생 조회를 수행하는 Spring Data JPA 기술 저장소다. */
public interface SpringDataNoticeRepository extends JpaRepository<NoticeJpaEntity, Long> {

    /* 회사와 활성 상태를 적용하고 본문을 제외한 목록 필드만 최신순으로 조회한다. */
    List<NoticeListProjection> findAllProjectedByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long companyId
    );

    /* NOTI-01 SELECT 컬럼을 식별자·제목·생성 시각으로 제한하는 닫힌 프로젝션이다. */
    interface NoticeListProjection {

        /* 공지 목록 행을 식별하는 기본 키를 반환한다. */
        Long getId();

        /* 목록에 표시할 공지 제목을 반환한다. */
        String getTitle();

        /* 최신순 정렬과 화면 표시가 사용하는 생성 시각을 반환한다. */
        LocalDateTime getCreatedAt();
    }
}
