package com.module06.backend.identity.member.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쓰기 가능한 role(구 sub_team) 매핑 — 온보딩 커밋(§4-1) 전용. 화면 표시용 읽기 전용
 * {@link RoleRefEntity}와 같은 테이블을 매핑하지만, 그쪽은 {@code @Immutable}이라 INSERT를
 * 타지 않으므로 이 엔티티의 IDENTITY 채번과 충돌하지 않는다.
 */
@Entity
@Table(name = "role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleWriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "name")
    private String name;

    private RoleWriteEntity(Long companyId, Long teamId, String name) {
        this.companyId = companyId;
        this.teamId = teamId;
        this.name = name;
    }

    static RoleWriteEntity create(Long companyId, Long teamId, String name) {
        return new RoleWriteEntity(companyId, teamId, name);
    }
}
