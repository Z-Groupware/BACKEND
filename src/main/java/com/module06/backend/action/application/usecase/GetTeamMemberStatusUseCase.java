package com.module06.backend.action.application.usecase;

import java.util.List;

import com.module06.backend.action.domain.repository.ActionReferenceRepository.ReferenceMemberStatus;

/* comment.
    2026-08-11, 이슈 #352 — 팀 대시보드 "팀원 현황" 테이블. 로스터(이름·직급·역할·재직상태)는
    identity(B) 소유 member 테이블을 action의 로컬 참조 엔티티(MemberReferenceEntity, 읽기 전용
    미러)로 읽고, actionCount만 action(C) 자신의 집계다 — Port 요청 없이 완결된다(#352 판단 기록).

    연결된 클래스
    - TeamMemberStatusService : 구현체 (application.service)
    - TeamMemberController    : 호출자 (presentation)
*/
public interface GetTeamMemberStatusUseCase {

    TeamMemberStatusList getTeamMemberStatus(Long teamId);

    // positionName·roleName은 각각 position_id·role_id가 null이면 null(직급·역할 미지정,
    // 방어적 처리 — member.role_id는 실제로는 NOT NULL이라 항상 존재하지만 원본 테이블
    // 컨벤션에 맞춰 null 케이스를 열어둔다). actionCount는 담당 PERSONAL 액션 총 건수.
    record TeamMemberItem(
            Long memberId, String name, String positionName, String roleName,
            ReferenceMemberStatus status, long actionCount) {
    }

    record TeamMemberStatusList(List<TeamMemberItem> items) {
    }
}
