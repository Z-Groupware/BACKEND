package com.module06.backend.identity.member.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쓰기 가능한 role(구 sub_team) 매핑 — 온보딩 커밋(§4-1)과 부서 체계 화면의 역할 CRUD
 * (§6-10~6-12)가 함께 쓴다. 화면 표시용 읽기 전용 {@link RoleRefEntity}와 같은 테이블을
 * 매핑하지만, 그쪽은 {@code @Immutable}이라 INSERT를 타지 않으므로 이 엔티티의 IDENTITY
 * 채번과 충돌하지 않는다.
 *
 * <p>{@code UK_ROLE_TEAM_NAME}(V2.3.23)을 매핑에도 적는다. 운영 스키마는 마이그레이션이
 * 만들지만, 테스트는 Hibernate {@code create-drop} 으로 스키마를 만들어 마이그레이션을 타지
 * 않는다 — 여기 없으면 제약 위반을 {@code ROLE_NAME_DUPLICATED} 로 바꾸는 경로를 테스트가
 * 통과시켜 버린다(제약이 없으니 애초에 위반이 나지 않는다).
 */
@Entity
@Table(name = "role", uniqueConstraints = @UniqueConstraint(
        name = "UK_ROLE_TEAM_NAME", columnNames = {"team_id", "name"}))
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
