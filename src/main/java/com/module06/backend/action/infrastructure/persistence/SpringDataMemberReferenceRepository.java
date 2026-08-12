package com.module06.backend.action.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    MemberReferenceEntity 조회 전용. 수동 생성(FR-AC-01 예외 경로)의 assigneeMemberId가
    같은 회사 소속인지 검증하는 데 쓴다. existsByIdAndTeamId는 2026-08-11 추가 — 팀장이
    팀원의 개인 액션 목록을 조회할 때 같은 팀 소속인지 확인.
    findAllByTeamIdAndDeletedAtIsNull은 2026-08-11 추가 — 팀 대시보드 "팀원 현황" 로스터
    조회용. 퇴사자(deleted_at 있음)는 로스터에서 제외한다.
*/
public interface SpringDataMemberReferenceRepository extends JpaRepository<MemberReferenceEntity, Long> {

    boolean existsByIdAndCompanyId(Long id, Long companyId);

    boolean existsByIdAndTeamId(Long id, Long teamId);

    List<MemberReferenceEntity> findAllByTeamIdAndDeletedAtIsNull(Long teamId);
}
