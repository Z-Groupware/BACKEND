package com.module06.backend.calendar.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    personal_todo(V6.1.1) 매핑.

    연결된 클래스
    - PersonalTodoRepository        : 구현하는 도메인 계약 (domain.repository)
    - PersonalTodoPersistenceAdapter : 이 엔티티 ↔ PersonalTodo 변환 (infrastructure.persistence)
*/
@Entity
@Table(name = "personal_todo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalTodoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "is_done", nullable = false)
    private boolean isDone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private PersonalTodoJpaEntity(
            Long id, Long companyId, Long memberId, String title, LocalDate date, boolean isDone
    ) {
        this.id = id;
        this.companyId = companyId;
        this.memberId = memberId;
        this.title = title;
        this.date = date;
        this.isDone = isDone;
    }
}
