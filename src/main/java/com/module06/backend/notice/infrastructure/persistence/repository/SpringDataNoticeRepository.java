package com.module06.backend.notice.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.module06.backend.notice.infrastructure.persistence.entity.NoticeJpaEntity;

/* notice 테이블의 파생 조회를 수행하는 Spring Data JPA 기술 저장소다. */
public interface SpringDataNoticeRepository extends JpaRepository<NoticeJpaEntity, Long> {

    /* 회사와 활성 상태를 적용하고 본문을 제외한 목록 필드만 최신순으로 조회한다. */
    List<NoticeListProjection> findAllProjectedByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long companyId
    );

    /* 공지 식별자·회사·활성 조건을 함께 적용해 타 회사와 삭제 공지를 빈 결과로 숨긴다. */
    Optional<NoticeJpaEntity> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    /* 수정·삭제 명령이 같은 활성 공지 행을 동시에 변경하지 못하도록 쓰기 잠금으로 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NoticeJpaEntity> findForUpdateByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

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
