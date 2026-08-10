package com.module06.backend.notice.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.notice.domain.model.Notice;

/* notice 테이블을 매핑하고 공지 도메인과 데이터베이스 컬럼을 연결하는 JPA 엔티티다. */
@Entity
@Table(name = "notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeJpaEntity {

    /* 데이터베이스가 생성하는 공지 기본 키다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 공지 조회의 테넌트 범위가 되는 회사 식별자다. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /* 목록과 상세에 표시할 공지 제목이다. */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /* 상세 화면에서 제공할 공지 본문이다. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /* 공지를 작성한 구성원 식별자다. */
    @Column(name = "created_by")
    private Long createdBy;

    /* 소프트 삭제된 시각이며 활성 공지에서는 null이다. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /* 공지 생성 시 데이터베이스에 기록되는 시각이다. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /* 공지가 마지막으로 수정된 시각이다. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /* 테스트와 이후 작성 API가 명시한 공지 원본을 엔티티로 변환한다. */
    private NoticeJpaEntity(Notice notice) {
        /* 도메인 모델의 전체 상태를 동일한 의미의 데이터베이스 컬럼에 대응시킨다. */
        this.id = notice.getId();
        this.companyId = notice.getCompanyId();
        this.title = notice.getTitle();
        this.content = notice.getContent();
        this.createdBy = notice.getCreatedBy();
        this.deletedAt = notice.getDeletedAt();
        this.createdAt = notice.getCreatedAt();
        this.updatedAt = notice.getUpdatedAt();
    }

    /* 공지 도메인 모델을 저장 가능한 영속성 엔티티로 변환한다. */
    public static NoticeJpaEntity from(Notice notice) {
        /* 컬럼 매핑 책임을 엔티티에 모아 어댑터의 반복 변환을 피한다. */
        return new NoticeJpaEntity(notice);
    }

    /* 저장된 공지 전체 상태를 프레임워크 독립적인 도메인 모델로 복원한다. */
    public Notice toDomain() {
        /* 식별자와 생명주기 시각을 포함한 모든 영속성 값을 도메인에 전달한다. */
        return Notice.reconstitute(id, companyId, title, content, createdBy, deletedAt, createdAt, updatedAt);
    }
}
