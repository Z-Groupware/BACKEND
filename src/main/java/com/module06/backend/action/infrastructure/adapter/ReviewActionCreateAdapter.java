package com.module06.backend.action.infrastructure.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.capture.application.port.out.ReviewActionCreatePort;

/* comment.
    action(C)이 구현하는 검토(A) 아웃바운드 포트. A가 정의한 ReviewActionCreatePort
    (capture.application.port.out)를 실제로 배선한다 — ActionReviewApplyAdapter와 같은 방향이다
    (2026-08-07, RVW-03 착수).

    Action.createManual을 쓰지 않는 이유: 그 팩터리는 FR-AC-01의 "+" 경로용이라 sourceMeetingId와
    evidenceTranscriptId를 받지 않는다. 검토 화면에서 추가한 액션은 회의에 매달려야 하고
    (RVW-01이 source_meeting_id로 찾는다), 사람이 원문에서 근거를 집어 넣은 경우 그 발화 id도
    남아야 한다. 그래서 create(분배 팩터리)로 만들고 applyHumanReview로 확정 상태를 얹는다 —
    두 단계 모두 애그리거트의 규칙을 지나므로 상태 규칙이 흩어지지 않는다.

    PERSONAL 고정이다. 검토 화면은 회의 참석자(사람) 단위로 액션을 묶어 보여주고 팀 선택 UI가
    없다 — 분석 경로가 PERSONAL만 생산하는 것과 같은 이유다(2026-08-06 확정).

    연결된 클래스
    - ReviewActionCreatePort : 구현하는 계약 (capture.application.port.out)
    - Action                 : create + applyHumanReview로 만들어지는 애그리거트
    - ActionRepository       : 저장 위임 대상
*/
@Component
@RequiredArgsConstructor
public class ReviewActionCreateAdapter implements ReviewActionCreatePort {

    private final ActionRepository actionRepository;

    @Override
    @Transactional
    public long createManual(ManualAction manual) {
        Action action = Action.create(
                manual.companyId(),
                manual.projectId(),
                /* parentActionId */ null,
                manual.meetingId(),
                /* teamId */ null,
                manual.assigneeMemberId(),
                ActionType.PERSONAL,
                manual.title(),
                manual.detail(),
                manual.dueDate(),
                /*
                 * dueDateDefaulted=false — 사람이 직접 넣은 날짜다. true로 두면 화면이
                 * "기본값이라 확인이 필요하다"고 표시하는데, 방금 사람이 고른 값이다.
                 */
                false,
                /*
                 * assigneeSource·evidence 근거는 AI 판정의 흔적이다. 수동 추가에는 판정이
                 * 없으므로 assigneeSource는 null이고, gateSignals도 마찬가지다 —
                 * 0점짜리 신호를 채우면 게이트를 통과하지 못한 AI 액션과 구분되지 않는다.
                 */
                null,
                manual.evidenceTranscriptId(),
                null,
                /* isManual */ true
        );

        /*
         * 확정 상태를 얹는다. 검토는 AI가 낸 것을 사람이 보는 절차인데, 사람이 방금 직접 쓴
         * 항목을 PENDING으로 두면 자기가 쓴 것을 자기가 다시 검토하는 화면이 된다.
         * 애그리거트가 confirmedAt까지 함께 찍는다.
         */
        action.applyHumanReview(null, null, ActionReviewStatus.HUMAN_CONFIRMED, null, null);

        return actionRepository.save(action).getId();
    }
}
