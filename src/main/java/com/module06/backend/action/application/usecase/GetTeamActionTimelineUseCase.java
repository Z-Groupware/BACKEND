package com.module06.backend.action.application.usecase;

import java.util.List;

import com.module06.backend.action.domain.model.Action;

/* comment.
    FR-AC-08 — 팀 액션 타임라인(?tab=timeline) 조회 기능 계약. 전 구성원 공개.
    해당 팀 액션(parentActionId)에 속한 팀원들의 개인 액션 목록을 내려준다.

    회사 스코프·TEAM 종류는 GetTeamActionDetailUseCase와 동일하게 다시 확인한다 — 다른 회사
    팀 액션 id나 PERSONAL 액션 id를 넣으면 존재하지 않는 것과 같은 404로 덮는다(#100과 동일 판단).

    projectTag·teamName은 담지 않는다 — 이미 팀 액션 상세 화면 안(같은 프로젝트·같은 팀)이라
    중복이다. 담당자명만 배치 조인해서 함께 내려준다.

    연결된 클래스
    - ActionRepository       : 팀 액션 존재 확인 + parentActionId 기준 하위 개인 액션 조회
    - ActionReferenceRepository : 담당자 이름 조인
    - ActionSummaryResponse  : 출력 DTO (presentation)
    - TeamActionController   : 호출자 (presentation)
*/
public interface GetTeamActionTimelineUseCase {

    List<TimelineItem> getTeamActionTimeline(Long companyId, Long teamActionId);

    record TimelineItem(Action action, String assigneeName) {
    }
}
