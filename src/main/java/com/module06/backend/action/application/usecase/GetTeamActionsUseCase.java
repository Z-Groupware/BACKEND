package com.module06.backend.action.application.usecase;

import java.util.List;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;

/* comment.
    FR-AC-06 — 팀 액션 목록 조회 기능 계약. JWT의 teamId로 자동 스코프된 LEADER 전용
    (Controller의 @PreAuthorize(hasRole)가 1차 방어, teamId 자체가 JWT에서만 나와 다른 팀
    조회가 애초에 불가능하므로 여기서 별도 소유권 검사를 하지 않는다).

    2026-08-10 페이지네이션 도입(이홍근 요청) — page는 0부터 시작.

    2026-08-10 필터/정렬 추가(이홍근 요청) — status는 null이면 필터 안 함. 팀 액션은 상태 변경이
    폐기됐지만(보드 대상 아님) 조회용 상태 컬럼은 여전히 있어 필터 가능. overdue는 안 받는다 —
    팀 액션은 보드/타임라인 "지연" 개념 자체가 없다(2026-08-07 폐기 결정과 동일선상).

    연결된 클래스
    - ActionRepository          : 조회
    - ActionReferenceRepository : 프로젝트태그·팀명 조인
    - ActionSummaryResponse     : 출력 DTO (presentation, 개인 액션 목록과 공용)
    - TeamActionController      : 호출자 (presentation)
*/
public interface GetTeamActionsUseCase {

    // companyId는 2026-08-11 추가(CodeRabbit #357 지적 반영) — 하위 개인 액션 진척 집계가
    // parentActionId만으로 조회되면 다른 회사의 PERSONAL 액션이 같은 parentActionId를 참조할 때
    // (같은 물리 테이블의 auto-increment id라 우연히 겹칠 수 있다) 그 회사 경계 밖 데이터가
    // 진척 카운트에 섞여 들어간다 — findAllByParentActionId(FR-AC-08)가 이미 companyId를
    // 필수로 받는 것과 동일한 이유.
    TeamActionListResult getTeamActions(
            Long teamId, Long companyId, ActionStatus status, String sort, String order, int page, int size);

    // projectName은 2026-08-11 추가(이홍근 요청, 목록 카드 프로젝트별 그룹핑용) — projectTag와
    // 같은 ProjectReference 배치조회에서 이미 갖고 있던 값이라 추가 쿼리 없음.
    // childDoneCount·childTotalCount는 2026-08-11 추가(이슈 #355, 이홍근 요청) — 하위 개인
    // 액션 진척 게이지("3/5")용. 하위가 아직 없는 팀 액션도 0/0으로 채워진다(null 아님 —
    // "하위 없음"이 아니라 "진행률 0%"라 FE가 게이지를 그대로 그릴 수 있어야 한다).
    record TeamActionListItem(
            Action action, String projectTag, String projectName, String teamName,
            int childDoneCount, int childTotalCount) {
    }

    record TeamActionListResult(List<TeamActionListItem> items, long totalElements) {
    }
}
