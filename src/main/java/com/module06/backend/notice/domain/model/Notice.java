package com.module06.backend.notice.domain.model;

import java.time.LocalDateTime;

import lombok.Getter;

/*
 * 회사 공지의 원본 데이터를 보유하는 도메인 모델이다.
 *
 * 공지는 회사에 귀속되며 deletedAt을 채워 삭제 이력을 보존한다. NOTI-01은 이 모델 중
 * 식별자·제목·생성 시각만 외부에 공개한다.
 */
@Getter
public class Notice {

    /* 데이터베이스가 생성하는 공지 식별자다. */
    private final Long id;

    /* 공지가 노출되는 회사 식별자다. */
    private final Long companyId;

    /* 목록과 상세에 표시할 공지 제목이다. */
    private final String title;

    /* 상세 화면에서만 표시할 공지 본문이다. */
    private final String content;

    /* 공지를 작성한 OWNER 또는 ADMIN 구성원 식별자다. */
    private final Long createdBy;

    /* 공지가 소프트 삭제된 시각이며 활성 공지는 null이다. */
    private final LocalDateTime deletedAt;

    /* 공지가 최초 저장된 시각이다. */
    private final LocalDateTime createdAt;

    /* 공지가 마지막으로 수정된 시각이며 수정 전에는 null일 수 있다. */
    private final LocalDateTime updatedAt;

    /* 영속성 원본을 프레임워크 독립적인 공지 모델로 복원한다. */
    private Notice(
            Long id,
            Long companyId,
            String title,
            String content,
            Long createdBy,
            LocalDateTime deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /* 저장된 값을 변형하지 않고 공지 원본 필드에 대응시킨다. */
        this.id = id;
        this.companyId = companyId;
        this.title = title;
        this.content = content;
        this.createdBy = createdBy;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /* 데이터베이스에서 읽은 공지의 전체 상태를 복원한다. */
    public static Notice reconstitute(
            Long id,
            Long companyId,
            String title,
            String content,
            Long createdBy,
            LocalDateTime deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /* 영속성 값의 의미를 그대로 유지한 공지 모델을 반환한다. */
        return new Notice(id, companyId, title, content, createdBy, deletedAt, createdAt, updatedAt);
    }
}
