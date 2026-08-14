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
 * 쓰기 가능한 role(구 sub_team) 매핑 — 온보딩 커밋(§4-1)과 부서 체계 화면의 역할 CRUD
 * (§6-10~6-12)가 함께 쓴다. 화면 표시용 읽기 전용 {@link RoleRefEntity}와 같은 테이블을
 * 매핑하지만, 그쪽은 {@code @Immutable}이라 INSERT를 타지 않으므로 이 엔티티의 IDENTITY
 * 채번과 충돌하지 않는다.
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

    /**
     * 이름만 바꾼다 — {@code team_id} 는 건드리지 않는다. 역할을 다른 부서로 옮기는 화면이 없고,
     * 옮기면 그 역할을 쓰던 사람들이 자기 부서에 없는 역할을 단 채로 남는다(§6-11).
     *
     * <p>이미 배정된 구성원에게는 자동으로 반영된다 — {@code member.role_id} 가 이 행을 가리키는
     * 참조라 조회하는 쪽이 바뀐 이름을 그대로 읽는다. 구성원 행은 건드리지 않는다.
     */
    void rename(String name) {
        this.name = name;
    }
}
