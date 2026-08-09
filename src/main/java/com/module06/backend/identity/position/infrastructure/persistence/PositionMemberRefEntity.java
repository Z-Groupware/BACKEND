package com.module06.backend.identity.position.infrastructure.persistence;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * position 도메인이 보는 member 의 읽기 전용 참조. team 쪽 {@code TeamMemberRefEntity} 와 같은 목적이다.
 * 쓰기 금지 — @Immutable 로 dirty checking 자체를 막는다.
 */
@Entity
@Table(name = "member")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionMemberRefEntity {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
