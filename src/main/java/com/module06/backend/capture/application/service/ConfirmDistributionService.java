package com.module06.backend.capture.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.ActionDispatchPort;
import com.module06.backend.capture.application.port.out.ActionDispatchPort.DispatchOutcome;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort.ReviewAction;
import com.module06.backend.capture.application.port.out.SttGapRepository;
import com.module06.backend.capture.application.result.DistributionConfirmed;
import com.module06.backend.capture.application.result.DistributionConfirmed.SkippedAction;
import com.module06.backend.capture.application.usecase.ConfirmDistributionUseCase;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * RVW-05 · 액션 분배 확정.
 *
 * **파이프라인의 마지막 사람 손이다.** 이 호출 전까지 액션은 만들어져 있어도 아무 데도 가 있지
 * 않고, 이 호출 뒤에 각자의 보드에 카드가 생긴다.
 *
 * <h2>되돌릴 수 없다는 것이 모든 판단의 근거다</h2>
 * 보드로 나간 액션을 회수하는 경로가 없다. 그래서 이 서비스는 **내보내지 않는 쪽으로 기운다** —
 * 확정되지 않은 것, 반려된 것, 담당자가 없는 것은 전부 남기고 그 사실을 응답에 적는다.
 *
 * <h2>강행(?confirm=true)은 관문을 여는 것이지 판정을 바꾸는 것이 아니다</h2>
 * 구멍이나 미검토가 남아 있어도 진행하겠다는 뜻일 뿐, **미검토 액션이 함께 나가지는 않는다.**
 * 강행이 "검토 안 한 것을 확정한다"가 되면 검토 화면 자체가 무의미해진다.
 *
 * <h2>담당자 없는 액션을 여기서 막는다</h2>
 * C 도메인은 AI 분배 경로에서 담당자 미정을 허용한다(2026-08-07 합의) — 회의에서 담당자가
 * 정해지지 않은 할 일이 검토 화면에서 통째로 사라지지 않게 하기 위해서다. 대신 **보드로 나가는
 * 것을 막는 안전장치가 이쪽 몫**이다. 담당자 없는 액션이 나가면 아무도 자기 일로 보지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmDistributionService implements ConfirmDistributionUseCase {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_REJECTED = "REJECTED";

    private static final String SKIP_STILL_PENDING = "STILL_PENDING";
    private static final String SKIP_REJECTED = "REJECTED";
    private static final String SKIP_NO_ASSIGNEE = "NO_ASSIGNEE";
    private static final String SKIP_ALREADY_DISPATCHED = "ALREADY_DISPATCHED";

    private final ActionReviewQueryPort actionReviewQueryPort;
    private final ActionDispatchPort actionDispatchPort;
    private final SttGapRepository sttGapRepository;
    private final MeetingAccessGuard meetingAccessGuard;
    private final MeetingHostProvider meetingHostProvider;

    /*
     * ⚠ 프로젝트 전체에 Clock 빈이 하나뿐이라(MeetingTimeConfiguration#meetingClock, KST)
     * 타입으로 주입된다. 캡처 전용 Clock 빈을 새로 만들면 안 된다.
     */
    private final Clock clock;

    @Override
    @Transactional
    public DistributionConfirmed confirm(ConfirmDistributionCommand command) {
        meetingAccessGuard.requireAccessible(command.companyId(), command.meetingId());
        requireHost(command);

        List<ReviewAction> actions = actionReviewQueryPort
                .findByMeeting(command.companyId(), command.meetingId(), null);

        List<Long> dispatchTargets = new ArrayList<>();
        List<SkippedAction> skipped = new ArrayList<>();
        for (ReviewAction action : actions) {
            String skipReason = skipReasonOf(action);
            if (skipReason == null) {
                dispatchTargets.add(action.actionId());
            } else {
                skipped.add(new SkippedAction(action.actionId(), skipReason));
            }
        }

        requirePassable(command, skipped);

        /*
         * 확정 시각을 여기서 한 번 읽어 전부에 같은 값을 찍는다. 어댑터가 각자 now() 를 부르면
         * 같은 확정으로 나간 액션의 시각이 갈리고, "이 회의를 언제 내보냈나"가 하나로 안 읽힌다.
         */
        LocalDateTime dispatchedAt = LocalDateTime.now(clock);
        DispatchOutcome outcome = dispatchTargets.isEmpty()
                ? DispatchOutcome.none()
                : actionDispatchPort.markDispatched(command.companyId(), dispatchTargets, dispatchedAt);

        /*
         * 이미 나가 있던 액션도 사람에게 말한다.
         *
         * 확정은 한 번으로 끝나지 않는다 — 확정 뒤에 액션을 더 넣고(RVW-03) 다시 누를 수 있고,
         * 그때 이전에 나간 것들이 대상에 함께 들어온다. 응답에 안 적으면 화면은 "10건 중 2건만
         * 나갔다"로 보이고 **사람은 나머지 8건이 실패한 줄 안다.**
         */
        outcome.alreadyDispatched()
                .forEach(actionId -> skipped.add(new SkippedAction(actionId, SKIP_ALREADY_DISPATCHED)));

        if (outcome.accountedFor() != dispatchTargets.size()) {
            /*
             * 확인되지 않은 액션이 있다 — 그 사이에 지워진 것이다. 오류로 올리지 않는다.
             * 나머지 확정을 되돌리면 사람이 다시 눌러야 하고 결과는 같다.
             *
             * 이미 나간 것을 함께 세는 것이 요점이다. 새로 찍힌 수만 비교하면 **두 번째
             * 확정마다 이 경고가 뜬다** — 정상 동작인데 사고처럼 보인다.
             */
            log.warn("분배 대상 중 확인되지 않은 액션이 있다 — meetingId={} 대상={} 확인={}",
                    command.meetingId(), dispatchTargets.size(), outcome.accountedFor());
        }

        log.info("분배 확정 — meetingId={} 확정한사람={} 새로내보냄={} 이미나감={} 남김={} 강행={}",
                command.meetingId(), command.requestedBy(), outcome.newlyDispatched(),
                outcome.alreadyDispatched().size(), skipped.size(), command.force());

        /*
         * dispatchedCount 와 dispatchedAt 은 **이번에 새로 나간 것**만 가리킨다. 이미 나간
         * 액션은 그때의 시각을 유지하므로 여기 숫자에 더하면 "방금 몇 건이 나갔나"가 부풀고,
         * 화면이 같은 액션을 두 번 보낸 것처럼 말하게 된다.
         */
        return new DistributionConfirmed(outcome.newlyDispatched(), dispatchedAt, List.copyOf(skipped));
    }

    /*
     * 회의 담당자만 확정할 수 있다(명세 RVW-05 · 403).
     *
     * 회사 관문(MeetingAccessGuard)과 다른 층이다. 그쪽은 "이 회의가 그 사람 회사 것인가"를 보고,
     * 여기는 같은 회사 안에서 **누가 마지막 버튼을 누르는가**를 본다. 검토(RVW-02)는 참석자
     * 누구나 하되 분배 확정은 한 사람이다 — 되돌릴 수 없기 때문이다.
     */
    private void requireHost(ConfirmDistributionCommand command) {
        long host = meetingHostProvider.hostMemberIdOf(command.meetingId())
                // 담당자를 모르는 채 통과시키면 이 검사는 없는 것과 같다.
                .orElseThrow(() -> new BusinessException(CaptureErrorCode.REVIEW_CONFIRM_HOST_ONLY));

        if (host != command.requestedBy()) {
            throw new BusinessException(CaptureErrorCode.REVIEW_CONFIRM_HOST_ONLY);
        }
    }

    /*
     * 내보내지 않을 이유를 고른다. 없으면 null 이다.
     *
     * 순서가 있다 — 반려를 먼저 본다. 반려된 액션은 담당자도 비어 있을 수 있는데, 그때
     * NO_ASSIGNEE 로 적으면 사람이 "담당자만 채우면 나가겠구나"로 읽는다. 실제로는 반려된
     * 것이고 담당자를 채워도 나가지 않는다.
     */
    private String skipReasonOf(ReviewAction action) {
        if (STATUS_REJECTED.equals(action.reviewStatus())) {
            return SKIP_REJECTED;
        }
        if (STATUS_PENDING.equals(action.reviewStatus())) {
            return SKIP_STILL_PENDING;
        }
        if (action.assigneeMemberId() == null) {
            return SKIP_NO_ASSIGNEE;
        }
        return null;
    }

    /*
     * 관문 — 확인되지 않은 STT 구간이나 미검토 액션이 남아 있으면 409 다(?confirm=true 로만 강행).
     *
     * 두 조건을 한 코드로 묶는다(MEETING_409_5). 명세가 그렇게 정했고, 화면에서 하는 일도 같다 —
     * "그래도 진행할까요?" 모달 하나다. 사람이 무엇을 무시하는지는 응답의 skipped 와 처리 상태
     * (CAP-06 의 gaps)로 보인다.
     *
     * ⚠ 반려는 관문 대상이 아니다. 반려는 **사람이 이미 판단을 끝낸 것**이고, 그것 때문에
     * 확정이 막히면 반려한 액션이 있는 회의는 영원히 강행으로만 확정된다.
     */
    private void requirePassable(ConfirmDistributionCommand command, List<SkippedAction> skipped) {
        if (command.force()) {
            return;
        }

        boolean pendingLeft = skipped.stream()
                .anyMatch(skip -> SKIP_STILL_PENDING.equals(skip.reason()));
        int unresolvedGaps = sttGapRepository.countUnresolved(command.meetingId());

        if (pendingLeft || unresolvedGaps > 0) {
            log.info("분배 확정 거절 — meetingId={} 미검토={} 미확인구멍={}",
                    command.meetingId(), pendingLeft, unresolvedGaps);
            throw new BusinessException(CaptureErrorCode.REVIEW_CONFIRM_BLOCKED);
        }
    }
}
