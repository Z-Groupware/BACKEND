package com.module06.backend.action.infrastructure.adapter;

import java.time.LocalDate;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.capture.application.port.out.ActionReviewApplyPort;

/* comment.
    action(C)이 구현하는 검토(A) 아웃바운드 포트. A가 정의한 ActionReviewApplyPort
    (capture.application.port.out)를 실제로 배선한다 — 인수인계(E)의 ActionReassignPort를
    ActionReassignAdapter가 배선한 것과 같은 방향이다(2026-08-06, RVW-02 착수).

    A가 action을 직접 UPDATE하지 않는 이유: 상태 전이 규칙(담당자 없는 PERSONAL을 막는 것 ·
    확정 시각을 찍는 것 · 기한을 고치면 dueDateDefaulted를 내리는 것)이 Action 애그리거트의
    것이고, 그 규칙을 지나지 않는 쓰기 경로가 생기면 규칙이 두 곳에 흩어진다.

    회사 스코프를 다시 확인한다. A의 조회가 이미 company_id로 걸러 오지만, 이 포트는 공개된
    인바운드 경계이므로 여기서도 막는다 — 한 곳이 빠지면 그 경로만 조용히 뚫린다(#100).

    연결된 클래스
    - ActionReviewApplyPort : 구현하는 계약 (capture.application.port.out)
    - Action                : applyHumanReview로 판정을 반영하는 애그리거트
    - ActionRepository      : 조회·저장 위임 대상
*/
@Component
@RequiredArgsConstructor
public class ActionReviewApplyAdapter implements ActionReviewApplyPort {

    private final ActionRepository actionRepository;

    @Override
    @Transactional
    public void apply(long companyId, long actionId, Long assigneeMemberId, LocalDate dueDate, String reviewStatus) {
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 액션입니다: " + actionId));

        if (!Long.valueOf(companyId).equals(action.getCompanyId())) {
            throw new IllegalStateException("다른 회사의 액션입니다: " + actionId);
        }

        action.applyHumanReview(assigneeMemberId, dueDate, ActionReviewStatus.valueOf(reviewStatus));
        actionRepository.save(action);
    }
}
