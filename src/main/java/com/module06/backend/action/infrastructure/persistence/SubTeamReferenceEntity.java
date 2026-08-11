package com.module06.backend.action.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    identity(B, 윤종호) 소유 role 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    ⚠ 테이블명은 실제로 `role`이다 — 원래 `sub_team`이었으나 V2.3.4(rename_sub_team_table_to_role)로
    이미 개명됐다(회사 정착 전 스키마 정리, 이 클래스와 무관한 시점). member 쪽도 V2.3.2로
    `sub_team_id`→`role_id`로 같이 바뀌었다. identity 도메인 자신도 동일 테이블을 읽는
    RoleRefEntity(identity.member.infrastructure.persistence)를 이미 갖고 있다 — 그쪽 Javadoc에도
    "구 sub_team(V2.3.4)"라고 명시돼 있다.
    이 클래스 이름을 SubTeam으로 그대로 둔 이유: FE(이홍근)가 부르는 "역할" 개념과 클래스명을
    맞추고, member.authority(구 member.role, V2.3.1로 개명된 조직 권한 enum)와 이름이
    겹쳐 헷갈리는 걸 피하기 위해서다 — DB 컬럼/테이블명만 role이고, Java 쪽 어휘는 의도적으로
    다르게 유지한다. role은 team(부서, 리더 1명)과 다른 개념 — "역할" 분류 태그일 뿐 리더가
    없다(예: 개발팀 아래 프론트엔드/백엔드, 이홍근 확인·2026-08-11). member.role_id로 조인되며
    2("없음")가 기본값이라 실제로는 항상 존재하지만, 방어적으로 null도 처리한다. 쓰기 금지 — @Immutable.

    연결된 클래스
    - ActionService                    : 담당자 역할 라벨 표시(GetActionDetailUseCase 구현)
    - MemberReferenceEntity            : role_id 조인의 반대편
    - ActionReferenceRepositoryAdapter : 배치 조회
*/
@Entity
@Table(name = "role")
@Immutable
@Getter
@NoArgsConstructor
public class SubTeamReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "name")
    private String name;
}
