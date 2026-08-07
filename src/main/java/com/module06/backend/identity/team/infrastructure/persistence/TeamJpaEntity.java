package com.module06.backend.identity.team.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 쓰기 가능한 team 매핑. member 쪽의 읽기 전용 TeamRefEntity 와는 별개다(도메인마다 다른 쓰기 책임). */
@Entity
@Table(name = "team")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "name")
    private String name;

    @Column(name = "leader_member_id")
    private Long leaderMemberId;

    private TeamJpaEntity(Long companyId, String name) {
        this.companyId = companyId;
        this.name = name;
    }

    static TeamJpaEntity create(Long companyId, String name) {
        return new TeamJpaEntity(companyId, name);
    }

    void rename(String name) {
        this.name = name;
    }

    void updateLeader(Long leaderMemberId) {
        this.leaderMemberId = leaderMemberId;
    }
}
