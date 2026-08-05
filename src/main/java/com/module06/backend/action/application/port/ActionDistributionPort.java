package com.module06.backend.action.application.port;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.action.domain.model.ActionType;

/* comment.
    action(C)이 선언하고, A(캡처) 도메인(이태연)이 호출하는 인바운드 포트.
    A가 회의 분석 직후 팀 액션·개인 액션을 일괄 생성한다(FR-AC-01 정상 경로) —
    보드 노출 여부는 생성 시점이 아니라 review_status로 걸러진다(08/05 확정).

    연결된 클래스
    - Action                : 이 포트를 통해 생성되는 도메인 모델
    - ActionTypeShapePolicy : 생성 시 종류별 필드 조합 검증 (domain.policy)
    - ActionRepository      : 생성된 액션 저장
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

    enum AssigneeSource {
        EXPLICIT_CALL,
        FIRST_PERSON
    }
}
