package com.module06.backend.action.application.port;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.model.AssigneeSource;

/* comment.
    action(C)이 선언하고, A(캡처) 도메인(이태연)이 호출하는 인바운드 포트.
    A가 회의 분석 직후 팀 액션·개인 액션을 일괄 생성한다(FR-AC-01 정상 경로) —
    보드 노출 여부는 생성 시점이 아니라 review_status로 걸러진다(08/05 확정).
    AssigneeSource는 원래 이 파일의 중첩 enum이었으나 Action(domain)이 필드로 가져야 해서
    domain.model로 옮겼다 — 필드명·타입명·시그니처는 그대로다(2026-08-06).

    이 계약은 teamId·parentActionId를 받지 않는다. TEAM 액션의 대상 팀은 회의의 team_id에서,
    PERSONAL 액션의 상위 팀 액션은 회의의 related_action_id(V3.1.1)에서 C가 유도한다
    (결정로그 25번, 요구사항명세서 6절).

    연결된 클래스
    - Action                    : 이 포트를 통해 생성되는 도메인 모델
    - ActionDistributionService : 이 포트의 구현체 (application.service)
    - ActionTypeShapePolicy     : 생성 시 종류별 필드 조합 검증 (domain.policy)
    - ActionRepository          : 생성된 액션 저장
*/
public interface ActionDistributionPort {

    List<DistributedAction> distribute(DistributeActionsCommand command);

    record DistributeActionsCommand(List<ActionDistributionItem> items) {
    }

    // 분배 입력 1건 — assigneeSource/evidenceTranscriptId/gateSignals는 자동확정 판정·근거 추적용.
    record ActionDistributionItem(
            String title,
            String description,
            ActionType actionType,
            Long assigneeMemberId,
            LocalDate dueDate,
            Long sourceMeetingId,
            Long projectId,
            Long companyId,
            AssigneeSource assigneeSource,
            Long evidenceTranscriptId,
            String gateSignals,
            boolean isManual
    ) {
    }

    // 생성된 액션 — actionId는 서버가 채번, source로 원본 분배 입력을 그대로 돌려준다.
    record DistributedAction(Long actionId, ActionDistributionItem source) {
    }
}
