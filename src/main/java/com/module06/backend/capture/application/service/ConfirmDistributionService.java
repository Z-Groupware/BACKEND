package com.module06.backend.capture.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.capture.application.port.out.ActionDispatchPort;
import com.module06.backend.capture.application.port.out.ActionDispatchPort.DispatchOutcome;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort.ReviewAction;
import com.module06.backend.capture.application.port.out.SttGapRepository;
import com.module06.backend.capture.application.result.DistributionConfirmed;
import com.module06.backend.capture.application.result.DistributionConfirmed.SkippedAction;
import com.module06.backend.capture.application.usecase.ApplyReviewDecisionUseCase;
import com.module06.backend.capture.application.usecase.ApplyReviewDecisionUseCase.ReviewDecisionCommand;
import com.module06.backend.capture.application.usecase.ConfirmDistributionUseCase;
import com.module06.backend.capture.domain.model.ReviewDecision;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.in.MeetingActionConfirmationPort;

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
 * <h2>2026-08-11 — [액션 분배 확정] 버튼이 API 호출 한 번으로 끝난다(이홍근 요청)</h2>
 * 예전엔 FE가 손 안 댄(PENDING) 항목마다 미리 CONFIRM PATCH를 쏘고 나서야 이 엔드포인트를
 * 불러야 했다. 이제 확정 시도 직전에, 담당자가 있는(또는 TEAM인) PENDING 항목을
 * ApplyReviewDecisionUseCase(RVW-02)의 검증된 CONFIRM 경로로 여기서 대신 호출한다 — 새
 * 규칙을 추가하는 게 아니라 이미 있는 단건 경로를 반복 호출하는 것뿐이라 review_log·
 * few-shot 벡터도 손으로 눌렀을 때와 똑같이 남는다.
 * 담당자 없는 PERSONAL 액션은 여전히 CONFIRM할 수 없다(ApplyReviewDecisionService의
 * requireAssigneeForConfirm) — 그런 항목은 그대로 PENDING으로 남아 아래 skipReasonOf에서
 * STILL_PENDING으로 스킵된다. 그래서 여기서 미리 걸러 apply()를 아예 안 부른다 — apply()가
 * @Transactional 로 이 트랜잭션에 합류한 채로 예외를 던지면, 여기서 catch 해도 트랜잭션은
 * 이미 rollback-only로 표시돼 confirm() 전체가 실패한다.
 *
 * <h2>담당자 없는 액션을 여기서 막는다</h2>
 * C 도메인은 AI 분배 경로에서 담당자 미정을 허용한다(2026-08-07 합의) — 회의에서 담당자가
 * 정해지지 않은 할 일이 검토 화면에서 통째로 사라지지 않게 하기 위해서다. 대신 **보드로 나가는
 * 것을 막는 안전장치가 이쪽 몫**이다. 담당자 없는 액션이 나가면 아무도 자기 일로 보지 않는다.
 *
 * <h2>2026-08-15 — 코드가 이어 준 담당자도 여기서 막는다</h2>
 * 근접 매칭(V5.22)이 담당자를 채우기 시작하면서 위 안전장치에 구멍이 생겼다. 담당자가 비어
 * 막히던 항목이 "담당자 있는 PENDING"이 되어 암묵 확정으로 그대로 나갔다. 추측으로 채운 값이
 * 사람 눈을 건너뛰는 경로라 nearMatchedAssignee 로 다시 막는다(자세한 근거는 그 메서드 주석).
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
    private final ApplyReviewDecisionUseCase applyReviewDecisionUseCase;
    private final MeetingActionConfirmationPort meetingActionConfirmationPort;

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

        int implicitlyConfirmed = confirmReviewablePendingActions(command);
        if (implicitlyConfirmed > 0) {
            log.info("확정 버튼 단일 호출로 암묵 확정 — meetingId={} 건수={}",
                    command.meetingId(), implicitlyConfirmed);
        }

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

        /* 같은 트랜잭션과 동일한 시각으로 D 회의의 최초 분배 확정을 기록한다. */
        meetingActionConfirmationPort.confirmActions(command.companyId(), command.meetingId(), dispatchedAt);

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
     * 2026-08-11 — 아직 PENDING인데 CONFIRM할 수 있는(담당자가 있거나 TEAM인) 항목을 암묵
     * 확정한다. ApplyReviewDecisionUseCase(RVW-02)의 검증된 CONFIRM 경로를 그대로 재사용 —
     * review_log·few-shot 벡터가 손으로 CONFIRM을 눌렀을 때와 동일하게 남는다.
     *
     * 담당자 없는 PERSONAL 액션은 미리 걸러서 apply()를 아예 안 부른다. apply()가 던지는
     * REVIEW_ASSIGNEE_REQUIRED를 여기서 catch 해도 소용없다 — apply()도 @Transactional이라
     * 이 메서드와 같은 트랜잭션에 합류하고, 예외가 나가는 순간 그 트랜잭션은 rollback-only로
     * 표시된다. catch로 무시해도 마지막에 커밋이 막혀 확정 전체가 실패한다.
     */
    private int confirmReviewablePendingActions(ConfirmDistributionCommand command) {
        List<ReviewAction> pending = actionReviewQueryPort
                .findByMeeting(command.companyId(), command.meetingId(), STATUS_PENDING);

        int confirmed = 0;
        for (ReviewAction action : pending) {
            boolean reviewable = action.assigneeMemberId() != null || action.actionType() == ActionType.TEAM;
            if (!reviewable || nearMatchedAssignee(action)) {
                continue;
            }
            /*
             * plannedStartDate 는 null 이다(마지막 인자). 일괄확정은 "AI 값이 다 맞다"를
             * 회의 단위로 한 번 누르는 동작이고, 액션마다 예정 시작일을 고르는 자리가 아니다 —
             * 그건 RVW-02 개별 판정에서 한다. 여기서 임의로 채우면 사람이 정하지 않은 날짜가
             * 타임라인에 그려진다.
             */
            applyReviewDecisionUseCase.apply(new ReviewDecisionCommand(
                    command.companyId(), command.meetingId(), action.actionId(), command.requestedBy(),
                    ReviewDecision.CONFIRM, null, null, null, null, null, null, null));
            confirmed++;
        }
        return confirmed;
    }

    /*
     * 담당자를 **코드가 이어 준** 액션인가(V5.22 · NearNameAssigneeResolver).
     *
     * <h2>왜 암묵 확정에서 빼는가</h2>
     * 근접 매칭이 붙기 전, 이름이 잘못 들린 배정은 담당자가 비어 있었고 그래서 이 경로를
     * 지나지 못했다(위 reviewable 검사) — 사람이 검토 화면에서 담당자를 지정해야 나갔다.
     * 근접 매칭이 그 자리를 채우면서 **같은 항목이 "담당자 있는 PENDING"이 되어 확정 버튼
     * 한 번에 그대로 나가게 됐다.** 사람이 개별로 보는 단계가 사라진 것이다.
     *
     * L7 자동확정 게이트에서 빼는 것으로는 막히지 않는다. 그건 검토 화면의 묶음을 가르는
     * 값이고(AutoConfirmGate), 보드로 나갈지는 이 서비스가 정한다. 막을 자리가 여기다.
     *
     * <h2>왜 조여야 하는가</h2>
     * 코드가 이은 담당자는 **글자 하나 차이로 추측한 값**이다. 오답이 0 건으로 측정됐지만
     * 표본이 회의 1 건·배정 4 건이라 그 숫자로 사람 눈을 건너뛸 근거가 되지 않는다. 그리고
     * 이 서비스가 지키는 성질이 "보드로 나간 액션은 회수할 수 없다"이므로, 확실하지 않은
     * 담당자는 내보내지 않는 쪽으로 기울어야 한다(클래스 주석).
     *
     * <h2>사라지지 않는다 — PENDING 으로 남는다</h2>
     * 빼기만 하므로 그 항목은 PENDING 그대로 남아 skipReasonOf 에서 STILL_PENDING 으로
     * 스킵되고, 미검토가 남았으므로 관문이 409 로 막는다(강행하면 그 항목만 빠진다).
     * 즉 host 가 그 담당자를 수용·수정·반려 중 하나로 **명시적으로 판정해야** 나간다 —
     * 근접 매칭 이전과 같은 상태이고, 다른 것은 화면에 이름 후보가 함께 보인다는 점뿐이다.
     *
     * ⚠ 새 스킵 사유를 만들지 않는다. 실제로 미검토가 맞으므로 STILL_PENDING 이 정확하고,
     * 왜 미검토로 남았는지는 RVW-01 응답의 assigneeNearMatched 가 말한다 — 화면은 그 값으로
     * "이름이 비슷해 연결됨"을 함께 보여주면 된다. 사유 코드를 늘리면 FE·BE 용어 매핑표에
     * 항목이 하나 더 붙는데, 그 표가 지금 비어 있다(2026-08-11 감사 F-05).
     *
     * NULL 은 빼지 않는다 — 근접 매칭 이전에 저장된 배정이거나 tuple 이 없는 수동 액션이다.
     * 그것까지 막으면 과거 회의의 확정이 통째로 멈춘다.
     */
    private boolean nearMatchedAssignee(ReviewAction action) {
        return Boolean.TRUE.equals(action.assigneeNearMatched());
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
        // TEAM 액션은 담당자 개념 자체가 없다(ActionTypeShapePolicy) — 이 검사를 actionType
        // 구분 없이 걸면 확정된 TEAM 액션이 영원히 NO_ASSIGNEE로 스킵되어 보드로 못 나간다
        // (2026-08-11, CodeRabbit 지적 — actionType이 이번 PR에서 ReviewAction에 추가되면서
        // 드러난 기존 버그).
        if (action.actionType() != ActionType.TEAM && action.assigneeMemberId() == null) {
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
