package com.module06.backend.meeting.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.meeting.domain.model.MeetingTopicType;

/*
 * meeting_topic 테이블의 회의 안건을 매핑하는 읽기·저장 엔티티다.
 *
 * 회의 엔티티와 JPA 연관관계를 만들지 않고 meetingId만 보관해 조회 경계와 배치 처리를 단순하게 유지한다.
 */
@Entity
@Table(name = "meeting_topic")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingTopicJpaEntity {

    /* 데이터베이스가 생성하는 회의 안건 기본 키다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 안건이 속한 회의 식별자다. */
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* SUB 안건이 속한 MAIN 안건 식별자이며 평면 구조에서는 null일 수 있다. */
    @Column(name = "parent_topic_id")
    private Long parentTopicId;

    /* 대주제 또는 소주제를 구분하는 유형이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "topic_type", nullable = false)
    private MeetingTopicType topicType;

    /* 회의 안건의 표시 내용이다. */
    @Column(name = "content", nullable = false, length = 300)
    private String content;

    /* 같은 회의 안에서 안건을 표시할 순서다. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /* 안건 행의 생성 시각이다. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /* 안건 행의 마지막 수정 시각이다. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /* 회의, 계층, 유형, 내용, 표시 순서로 테스트와 후속 안건 등록에서 사용할 엔티티를 생성한다. */
    public MeetingTopicJpaEntity(
            Long meetingId,
            Long parentTopicId,
            MeetingTopicType topicType,
            String content,
            int sortOrder
    ) {
        /* 전달받은 값만 저장하고 다른 도메인 엔티티는 참조하지 않는다. */
        this.meetingId = meetingId;
        this.parentTopicId = parentTopicId;
        this.topicType = topicType;
        this.content = content;
        this.sortOrder = sortOrder;
    }
}
