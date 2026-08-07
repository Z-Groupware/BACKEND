package com.module06.backend.capture.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort.ReviewTarget;
import com.module06.backend.capture.application.port.out.ReviewActionDeletePort;
import com.module06.backend.capture.application.usecase.CancelReviewActionUseCase;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * RVW-04 · 직접 추가한 액션 취소.
 *
 * 사람이 "+"로 넣었다가 취소(X)하는 자리다. 잘못 넣은 것을 그 자리에서 무를 수 있어야 한다.
 *
 * <h2>AI 액션은 지우지 않는다 — 이 서비스의 전부라고 해도 된다</h2>
 * `is_manual` 이 false 면 409 로 막는다(명세 RVW-04). 지우면 **라벨이 사라진다** —
 * 「AI 가 이런 걸 뽑았고 사람이 아니라고 했다」는 쌍이 정확도 개선의 재료이고, 그 판정을
 * 남기는 방법이 RVW-02 의 REJECT 다. 행이 없어지면 반려했다는 사실 자체가 없던 일이 되고,
 * 지나간 회의는 다시 만들 수 없어 되돌릴 수도 없다.
 *
 * <h2>회사 스코프를 두 겹으로 지난다</h2>
 * MeetingAccessGuard 가 회의를, 조회가 actionId 를 본다 — RVW-02 와 같은 자리다. 관문은
 * 회의까지만 보므로 회의는 내 것인데 actionId 만 남의 것을 넣는 경로가 남는다(#100).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelReviewActionService implements CancelReviewActionUseCase {

    private final ActionReviewQueryPort actionReviewQueryPort;
    private final ReviewActionDeletePort reviewActionDeletePort;
    private final MeetingAccessGuard meetingAccessGuard;

    @Override
    @Transactional
    public void cancel(CancelReviewActionCommand command) {
        meetingAccessGuard.requireAccessible(command.companyId(), command.meetingId());

        ReviewTarget target = actionReviewQueryPort
                .findOne(command.companyId(), command.meetingId(), command.actionId())
                .orElseThrow(() -> new BusinessException(CaptureErrorCode.REVIEW_ACTION_NOT_FOUND));

        /*
         * 판정을 조회 결과로 한다 — 삭제 포트에 맡기지 않는다. 명세가 요구하는 코드가
         * 409(MEETING_409_7)이고 그건 검토 화면의 규칙이라 A 가 답해야 한다. C 쪽에서 던지면
         * 그 도메인의 코드 체계로 나가 화면이 다른 분기를 타게 된다.
         */
        if (!target.manual()) {
            throw new BusinessException(CaptureErrorCode.REVIEW_DELETE_AI_ACTION);
        }

        reviewActionDeletePort.deleteManual(command.companyId(), target.actionId());

        log.info("직접 추가 액션 취소 — meetingId={} actionId={} 취소한사람={}",
                command.meetingId(), target.actionId(), command.requestedBy());
    }
}
