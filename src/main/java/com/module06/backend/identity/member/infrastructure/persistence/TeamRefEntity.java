package com.module06.backend.identity.member.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 팀명 표시용 읽기 전용 참조. 쓰기 금지 — @Immutable 로 dirty checking 자체를 막는다. */
@Entity
@Table(name = "team")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamRefEntity {

    @Id
    private Long id;

    @Column(name = "name")
    private String name;

    /**
     * 부서장. 인수인계의 중간 승인자를 찾을 때 쓴다(OrgQueryPort#findTeamLeaderId).
     *
     * <p>연관이 아니라 식별자 값으로 둔다 — 다른 도메인 엔티티와 연관을 만들지 않는 이 저장소 규칙에
     * 맞추고, 팀을 읽을 때마다 멤버가 딸려 오는 것도 막는다. null 이면 리더 미지정이다(V2.2.6).
     */
    @Column(name = "leader_member_id")
    private Long leaderMemberId;
}
