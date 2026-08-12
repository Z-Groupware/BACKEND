package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.capture.application.port.out.ActionReviewApplyPort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort.AiValue;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort.ReviewTarget;
import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.ReviewLogRepository;
import com.module06.backend.capture.application.port.out.ReviewLogRepository.ReviewLogEntry;
import com.module06.backend.capture.application.port.out.TupleVectorRepository;
import com.module06.backend.capture.application.port.out.TupleVectorRepository.VectorEntry;
import com.module06.backend.capture.application.result.ReviewDecisionOutcome;
import com.module06.backend.capture.application.usecase.ApplyReviewDecisionUseCase;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.RejectReason;
import com.module06.backend.capture.domain.model.ReviewDecision;
import com.module06.backend.capture.domain.model.ReviewTargetType;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * RVW-02 · 액션 항목 수정·반려.
 *
 * **사람이 확정하는 이 순간이 시스템에서 유일한 쓰기 지점이다**(명세 RVW-02). 그래서 세 곳이
 * 한 트랜잭션에 들어간다 — action(사람이 고친 값) · review_log(라벨) · meeting_tuple_vector
 * (few-shot 예시 예약). 나누면 액션은 반려됐는데 라벨이 없거나, 라벨은 있는데 액션이 그대로인
 * 상태가 남는다. 앞은 개선 재료를 잃는 것이고 뒤는 사람이 반려한 일이 보드로 가는 것이다.
 *
 * <h2>라벨이 이 API 의 본체다</h2>
 * 화면에서 보면 "담당자 바꾸고 저장"이지만, 이 저장소가 이 순간에 실제로 얻는 것은
 * **{AI 입력 → 사람이 인정한 정답} 한 쌍**이다. 그게 특화 모델의 유일한 연료이고, 지나간
 * 회의는 다시 만들 수 없다(V5.9 주석). 그래서 CONFIRM 도 기록한다 — 맞힌 것을 안 남기면
 * 라벨셋에 오답만 쌓여 분포가 왜곡된다.
 *
 * <h2>담당자 없는 액션은 여기서 채워야 확정된다</h2>
 * AI 분배는 담당자 미정을 허용한다(2026-08-07 합의) — 담당자가 안 정해진 할 일이 검토 화면에서
 * 통째로 사라지지 않게 하기 위해서다. 그 대신 **채우는 자리가 여기**이고, 안 채운 채로 확정되면
 * 담당자 null 이 정답 라벨로 학습되고 RVW-05 에서도 조용히 빠진다. 그래서 막는다
 * ({@link #requireAssigneeForConfirm}).
 *
 * <h2>회사 스코프를 두 겹으로 지난다</h2>
 * MeetingAccessGuard 가 회의를, 조회가 actionId 를 본다. 관문은 회의까지만 보므로 회의는 내
 * 것인데 actionId 만 남의 것을 넣는 경로가 남는다 — 그건 조회에서 걸러진다(#100 과 같은 자리).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyReviewDecisionService implements ApplyReviewDecisionUseCase {

    private static final String STATUS_HUMAN_CONFIRMED = "HUMAN_CONFIRMED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final ActionReviewQueryPort actionReviewQueryPort;
    private final ActionReviewApplyPort actionReviewApplyPort;
    private final ReviewLogRepository reviewLogRepository;
    private final TupleVectorRepository tupleVectorRepository;
    private final MeetingAccessGuard meetingAccessGuard;
    private final MeetingParticipantProvider meetingParticipantProvider;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ReviewDecisionOutcome apply(ReviewDecisionCommand command) {
        meetingAccessGuard.requireAccessible(command.companyId(), command.meetingId());
        requireDecisionShape(command);

        ReviewTarget target = actionReviewQueryPort
                .findOne(command.companyId(), command.meetingId(), command.actionId())
                .orElseThrow(() -> new BusinessException(CaptureErrorCode.REVIEW_ACTION_NOT_FOUND));

        /*
         * 참석자 명단을 한 번만 읽는다. 검증에도 쓰고 라벨의 input_context 에도 담는데,
         * 두 번 읽으면 그 사이에 명단이 바뀐 경우 **검증한 명단과 기록한 명단이 갈린다** —
         * 라벨을 나중에 보면 명단 밖 담당자가 통과한 것처럼 보인다.
         */
        List<AiLayerPort.Participant> roster = meetingParticipantProvider.participantsOf(command.meetingId());

        Long assignee = requireInRoster(command, roster);
        requireAssigneeForConfirm(command, target, assignee);
        String reviewStatus = command.decision() == ReviewDecision.REJECT
                ? STATUS_REJECTED
                : STATUS_HUMAN_CONFIRMED;

        /*
         * 반려는 값을 고치지 않는다. 담당자·기한·제목·내용을 함께 보내와도 무시한다 — 반려된
         * 액션의 값을 바꾸면 라벨의 llm_output 과 action 이 갈리고, "AI 가 무엇을 냈는지"를
         * 화면에서 되짚을 수 없다. 반려는 상태만 바뀐다.
         */
        if (command.decision() == ReviewDecision.REJECT) {
            actionReviewApplyPort.apply(command.companyId(), target.actionId(),
                    null, null, null, null, null, reviewStatus);
        } else {
            /*
             * 인자 순서에 LocalDate 가 둘이다(dueDate · plannedStartDate). 포트 주석의 경고가
             * 이 호출을 가리킨다 — 뒤집으면 컴파일되고 아무 예외도 안 나며, 기한과 예정
             * 시작일이 서로 바뀌어 저장된다.
             */
            actionReviewApplyPort.apply(command.companyId(), target.actionId(), assignee, command.dueDate(),
                    command.title(), command.detail(), command.plannedStartDate(), reviewStatus);
        }

        List<Long> reviewLogIds = appendReviewLogs(command, target, assignee, roster);
        /*
         * 대표 review_log는 첫 번째 것을 쓴다 — 벡터는 순수 추적용 컬럼이라(코드 전수 확인,
         * 어디서도 JOIN하지 않음) 여러 review_log 중 어느 것을 가리켜도 정확도에 영향 없다.
         *
         * ⚠ 라벨이 0건인 경우가 있다 — 예정 시작일만 고친 MODIFY(appendReviewLogs 주석).
         * 그때는 벡터도 예약하지 않는다. few-shot 예시는 라벨에 딸린 것이라 가리킬 라벨이
         * 없으면 예시로 쓸 값도 없고, review_log_id 를 비워 넣으면 나중에 그 예시가 어느
         * 판정에서 왔는지 되짚을 수 없다.
         */
        boolean vectorQueued = !reviewLogIds.isEmpty()
                && enqueueVector(command, target, assignee, reviewLogIds.get(0));

        log.info("검토 판정 — meetingId={} actionId={} decision={} reason={} 라벨={}건 벡터예약={}",
                command.meetingId(), target.actionId(), command.decision(), command.rejectReason(),
                reviewLogIds.size(), vectorQueued);

        return new ReviewDecisionOutcome(target.actionId(), reviewStatus, true, vectorQueued);
    }

    /*
     * 판정과 나머지 칸의 조합이 성립하는지 본다.
     *
     * <b>사유(2026-08-11 갱신)</b> — REJECT 에는 필수이고, CONFIRM·MODIFY 에는 붙을 수 없다.
     * MODIFY가 더 이상 rejectReason을 받지 않는 이유는 appendReviewLogs 주석 참고 — 여러 필드를
     * 동시에 고치면 사유 하나로 담을 수 없어서, 바뀐 필드로 BE가 자동 유도한다.
     * REJECT 사유는 사람이 직접 고른 5종만 허용한다(WRONG_* 는 MODIFY 전용이라 거절).
     *
     * <b>값</b> — CONFIRM 에는 담당자·기한·제목·내용을 함께 보낼 수 없다(이유는 기존과 동일).
     * <b>MODIFY 값</b> — 넷 다 null이면 "뭘 고쳤다는 건지" 알 수 없어 거절한다(2026-08-11 추가).
     */
    private void requireDecisionShape(ReviewDecisionCommand command) {
        boolean needsReason = command.decision() == ReviewDecision.REJECT;
        if (needsReason == (command.rejectReason() == null)) {
            throw new BusinessException(CaptureErrorCode.REVIEW_REASON_REQUIRED);
        }
        if (command.decision() == ReviewDecision.REJECT && !command.rejectReason().isHumanSelectable()) {
            throw new BusinessException(CaptureErrorCode.REVIEW_REASON_NOT_SELECTABLE);
        }
        /*
         * ⚠ plannedStartDate 는 이 검사에서 **일부러 빠진다**(#386 후속).
         *
         * 다른 넷은 "AI 가 낸 값을 고쳤다"는 뜻이라 CONFIRM(=AI 값이 맞다)과 함께 오면
         * 모순이다. 예정 시작일은 AI 가 내지 않는 값이라 고칠 대상 자체가 없고, 사람이
         * **처음 정하는** 값이다 — "AI 값은 다 맞으니 확정하고, 시작일만 정해 둔다"가
         * 자연스러운 조합이다. 여기서 막으면 화면이 확정 버튼을 누를 때마다 시작일을
         * 별도 요청으로 다시 보내야 한다.
         */
        if (command.decision() == ReviewDecision.CONFIRM
                && (command.assignee() != null || command.dueDate() != null
                    || command.title() != null || command.detail() != null)) {
            throw new BusinessException(CaptureErrorCode.REVIEW_CONFIRM_WITH_VALUE);
        }
        /*
         * MODIFY 쪽에는 **포함된다.** 예정 시작일만 보내온 MODIFY 는 "무엇을 고쳤는지"가
         * 분명하므로 거절할 이유가 없다 — 이 검사가 막는 것은 넷도 아니고 시작일도 아닌
         * 빈 요청이다.
         */
        if (command.decision() == ReviewDecision.MODIFY
                && command.assignee() == null && command.dueDate() == null
                && command.title() == null && command.detail() == null
                && command.plannedStartDate() == null) {
            throw new BusinessException(CaptureErrorCode.REVIEW_MODIFY_VALUE_REQUIRED);
        }
    }

    /*
     * 담당자를 고쳤으면 참석자 명단 안인지 본다.
     *
     * 사람이 드롭다운에서 고른 값이라 신뢰할 만해 보이지만, 명단 밖 id 를 직접 보내는 경로가
     * 남아 있다. 명단 밖 담당자는 회의에 없던 사람의 보드로 가고 **그 값이 정답 라벨로
     * 학습된다** — 틀린 배정을 AI 에게 가르치는 셈이다.
     *
     * 명단 밖 탈출구(personId=null)는 후보에서 뺀다. 그건 "명단에 없는 사람"을 나타내는
     * 자리이고 실제 사람이 아니다(L1·L6·L7 이 쓰는 것과 같은 집합).
     */
    private Long requireInRoster(ReviewDecisionCommand command, List<AiLayerPort.Participant> roster) {
        Long assignee = command.assignee();
        if (assignee == null || command.decision() == ReviewDecision.REJECT) {
            return null;
        }
        boolean inRoster = roster.stream()
                .map(AiLayerPort.Participant::personId)
                .filter(Objects::nonNull)
                .anyMatch(assignee::equals);
        if (!inRoster) {
            throw new BusinessException(CaptureErrorCode.REVIEW_ASSIGNEE_NOT_IN_ROSTER);
        }
        return assignee;
    }

    /*
     * 확정(CONFIRM·MODIFY)은 담당자가 정해진 뒤에만 지나간다.
     *
     * 담당자 미정인 액션이 여기까지 오는 것은 정상이다 — AI 분배 경로가 그 상태를 허용하고
     * (2026-08-07 합의 · ActionTypeShapePolicy#checkDistribution), **채우는 자리가 이 화면**이다.
     * 막는 것은 "안 채운 채로 확정되는 것"뿐이다.
     *
     * 이번 요청의 담당자를 먼저 보고, 없으면 액션의 현재 값을 본다. 순서가 반대면 담당자를
     * 채워 보낸 MODIFY 가 액션의 옛 값(null)때문에 거절된다 — applyHumanReview 가 값을 반영하는
     * 순서와 같게 맞춘 것이다.
     *
     * ⚠ 반려는 대상이 아니다. 반려는 값을 고치지 않고 상태만 바꾸며, 담당자를 못 정해서 버리는
     * 액션이 정확히 반려로 가는 길이다 — 여기서 막으면 그 길이 닫힌다.
     *
     * ⚠ TEAM 액션도 대상이 아니다. 담당자 개념이 없으므로(ActionTypeShapePolicy) 함께 막으면
     * 팀 액션은 영원히 확정되지 않는다. actionType 을 읽지 못한 경우(null)는 면제하지 않는다 —
     * 게이트는 조이는 방향으로만 쓴다.
     */
    private void requireAssigneeForConfirm(ReviewDecisionCommand command, ReviewTarget target, Long assignee) {
        if (command.decision() == ReviewDecision.REJECT || target.actionType() == ActionType.TEAM) {
            return;
        }
        if (assignee == null && target.assigneeMemberId() == null) {
            throw new BusinessException(CaptureErrorCode.REVIEW_ASSIGNEE_REQUIRED);
        }
    }

    /*
     * MODIFY는 고친 필드 개수만큼 review_log를 나눠 append한다(2026-08-11, 이홍근이 발견한
     * 설계 공백 해소).
     *
     * <h2>왜 나누나</h2>
     * review_log.reject_reason은 한 값만 담을 수 있는데, 화면은 담당자·기한·제목·내용을
     * 한 번의 PATCH로 동시에 고칠 수 있다. 하나만 골라 담으면 실제로는 여러 계층이 틀렸는데
     * 하나만 틀린 것으로 집계돼 정확도 측정이 왜곡된다.
     *
     * <h2>왜 스키마 변경이 없어도 되나</h2>
     * review_log는 원래 append-only 로그다(수정·삭제 메서드 자체가 없다, V5.9 주석 —
     * "같은 액션을 두 번 판정하면 행이 둘 남는 게 맞다"). append를 여러 번 부르는 것은 이
     * 원칙을 벗어나지 않는다.
     *
     * CONFIRM·REJECT는 기존처럼 항상 1건이다 — CONFIRM은 사유 없이(null), REJECT는 사람이
     * 고른 사유 그대로.
     */
    private List<Long> appendReviewLogs(ReviewDecisionCommand command, ReviewTarget target,
                                         Long assignee, List<AiLayerPort.Participant> roster) {
        if (command.decision() != ReviewDecision.MODIFY) {
            RejectReason reason = command.decision() == ReviewDecision.REJECT ? command.rejectReason() : null;
            return List.of(reviewLogRepository.append(logEntryOf(command, target, assignee, roster, reason)));
        }

        List<RejectReason> changedFieldReasons = new ArrayList<>();
        if (assignee != null) {
            changedFieldReasons.add(RejectReason.WRONG_ASSIGNEE);
        }
        if (command.dueDate() != null) {
            changedFieldReasons.add(RejectReason.WRONG_DUE);
        }
        if (command.title() != null) {
            changedFieldReasons.add(RejectReason.WRONG_TITLE);
        }
        if (command.detail() != null) {
            changedFieldReasons.add(RejectReason.WRONG_DETAIL);
        }
        /*
         * ⚠ plannedStartDate 에는 라벨을 만들지 않는다(#386 후속).
         *
         * 라벨의 뜻이 {AI 가 낸 것 → 사람이 인정한 정답}이다. 예정 시작일은 AI 가 애초에 내지
         * 않는 값이므로(meeting_assignment_tuple 에 컬럼이 없다) WRONG_* 를 붙이면 **모델이
         * 말한 적도 없는 것을 틀렸다고 가르치게 된다.** 그 라벨이 few-shot 예시로 뽑히면
         * 다음 회의의 프롬프트가 존재하지 않는 필드를 교정하려 든다.
         *
         * 대응하는 RejectReason 값도 없다. 새로 만들지 않는 것이 맞다 — 사유 목록은 "AI 가
         * 어느 계층에서 틀렸나"의 분류이고, 여기엔 틀린 계층이 없다.
         */
        if (changedFieldReasons.isEmpty()) {
            /*
             * 예정 시작일만 고친 MODIFY 다. **라벨을 남기지 않는다.**
             *
             * 처음에는 사유 없이 한 건 남기려 했는데, V5.9 의 CK_REVIEW_LOG_REASON 이
             * ACTION + MODIFY 에 사유를 **강제한다**(SUMMARY_ITEM 만 예외). 사유 null 로
             * INSERT 하면 제약 위반으로 **판정 트랜잭션 전체가 롤백된다** — 시작일 하나
             * 때문에 확정이 실패한다(CodeRabbit PR #422 지적).
             *
             * 마이그레이션으로 제약을 넓히지 않는다. 그 제약의 주석이 왜 조인지 적어 뒀고
             * ("액션 수정은 바뀐 필드로 사유를 자동 추론할 수 있다"), 넓히면 사유 없는 액션
             * 수정이 라벨셋에 섞인다. 제약은 여기서 **"사유 없는 ACTION MODIFY 는 유효한
             * 라벨이 아니다"** 를 말하고 있고, 그게 맞다 — 예정 시작일은 AI 가 내지 않는
             * 값이라 {AI 입력 → 정답} 쌍이 성립하지 않는다. 학습할 것이 없는 행이다.
             *
             * 판정 사실이 사라지는 것은 아니다. action.review_status 가 HUMAN_CONFIRMED 로
             * 바뀌고 confirmed_at 이 찍힌다 — "사람이 이 액션을 처리했다"는 그쪽에 남는다.
             * review_log 는 감사 로그가 아니라 **학습 라벨**이다(클래스 주석).
             */
            return List.of();
        }

        List<Long> ids = new ArrayList<>();
        for (RejectReason reason : changedFieldReasons) {
            ids.add(reviewLogRepository.append(logEntryOf(command, target, assignee, roster, reason)));
        }
        return ids;
    }

    /*
     * 남길 라벨 하나를 만든다.
     *
     * layer 는 사유가 정한다(RejectReason.layer). 사유가 없으면(CONFIRM) **액션을 만든
     * 계층(L4)** 으로 적는다 — 정답 라벨이 어느 계층의 정답인지 없으면 few-shot 조회가
     * 그 행을 못 찾는다(AI-09 가 layer 를 필수 필터로 요구한다).
     */
    private ReviewLogEntry logEntryOf(ReviewDecisionCommand command, ReviewTarget target,
                                      Long assignee, List<AiLayerPort.Participant> roster, RejectReason reason) {
        AiValue ai = target.aiValue();
        LayerName layer = reason != null ? reason.layer() : LayerName.L4;

        return new ReviewLogEntry(
                command.companyId(),
                command.meetingId(),
                ReviewTargetType.ACTION,
                target.actionId(),
                layer,
                command.decision(),
                reason,
                inputContextJson(target, roster),
                llmOutputJson(target),
                // CONFIRM 은 null 이다 — llm_output 과 같다는 뜻이고, 같은 값을 두 번 적으면
                // "고쳤는데 우연히 같았다"와 구분되지 않는다(V5.9 주석).
                command.decision() == ReviewDecision.CONFIRM
                        ? null
                        : humanValueJson(command, target, assignee),
                command.confirmedBy(),
                ai != null ? ai.modelName() : null,
                ai != null ? ai.promptVersion() : null,
                target.manual());
    }

    /*
     * 재현할 수 없는 값을 넉넉히 담는다(V5.9 주석). 근거 발화 · 주제 · 참석자 명단이다.
     *
     * 참석자 명단을 담는 이유 — L4 의 '닫힌 목록'이 그때 무엇이었는지가 라벨의 전제다.
     * 사람이 나중에 회사를 떠나면 명단을 다시 만들 수 없고, 그러면 "명단 안에서 골랐는가"를
     * 되짚을 수 없다.
     */
    private String inputContextJson(ReviewTarget target, List<AiLayerPort.Participant> roster) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("topic", target.topic());
        context.put("evidenceTranscriptId", target.evidenceTranscriptId());
        context.put("evidenceContent", target.evidenceContent());
        context.put("roster", rosterOf(roster));
        // AI-08·09 가 붙기 전이라 few-shot 을 쓴 적이 없다. 그 사실을 값으로 남긴다 —
        // 나중에 few-shot 을 켠 뒤 라벨을 비교할 때 이 구분이 필요하다.
        context.put("usedFewShot", false);
        return toJson(context, "{}");
    }

    private List<Map<String, Object>> rosterOf(List<AiLayerPort.Participant> roster) {
        return roster.stream()
                .map(participant -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    // personId 가 null 인 탈출구도 담는다 — 그 자리가 명단에 있었다는 사실이
                    // "명단 밖 사람을 가리킬 수 있었다"는 전제를 설명한다.
                    entry.put("memberId", participant.personId());
                    entry.put("name", participant.name());
                    return entry;
                })
                .toList();
    }

    /*
     * AI 가 낸 원본. tuple 이 없으면(수동 추가 액션) 빈 객체다.
     *
     * "{}" 로 두는 이유 — 컬럼이 NOT NULL 이고, null 대신 넣을 만한 값이 없다. is_manual 이
     * true 인 행이 그 사실을 함께 말해주므로 빈 객체가 무엇을 뜻하는지는 잃지 않는다.
     */
    private String llmOutputJson(ReviewTarget target) {
        AiValue ai = target.aiValue();
        if (ai == null) {
            return "{}";
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("title", ai.title());
        // detail은 항상 null이다 — meeting_assignment_tuple에 대응 컬럼이 없어 AI가 애초에
        // 내지 않는다(ActionReviewQueryPort.AiValue 주석 참고). 대칭성을 위해 키는 남긴다.
        output.put("detail", ai.detail());
        output.put("assigneeMemberId", ai.assigneeMemberId());
        output.put("assigneeSource", ai.assigneeSource() != null ? ai.assigneeSource().name() : null);
        output.put("dueDate", ai.dueDate() != null ? ai.dueDate().toString() : null);
        return toJson(output, "{}");
    }

    /*
     * 사람이 인정한 값.
     *
     * 고치지 않은 칸은 **액션의 현재 값**을 담는다. 바뀐 칸만 담으면 이 라벨 한 행만 보고
     * 정답을 복원할 수 없고, few-shot payload 를 만들 때 다른 표를 다시 읽어야 한다.
     * 2026-08-11 — title·detail도 같은 규칙으로 추가(예전엔 title이 수정 불가라 target 값
     * 고정이었는데, 이제는 command 값을 우선한다).
     */
    private String humanValueJson(ReviewDecisionCommand command, ReviewTarget target, Long assignee) {
        // REJECT는 값을 고치지 않는다(apply()가 이미 title·detail·dueDate를 null로 덮어 포트에
        // 넘긴다) — 그런데도 command에 값이 실려 오면(FE 실수든 악의든) 여기서 그 값을 그대로
        // 받아 적으면 반영되지도 않은 값이 정답 라벨로 남는다. REJECT일 때는 command를 아예
        // 안 보고 target의 현재 값만 쓴다(2026-08-11, CodeRabbit 지적).
        boolean rejected = command.decision() == ReviewDecision.REJECT;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", !rejected && command.title() != null ? command.title() : target.title());
        value.put("detail", !rejected && command.detail() != null ? command.detail() : target.detail());
        value.put("assigneeMemberId", assignee != null ? assignee : target.assigneeMemberId());
        LocalDate dueDate = !rejected && command.dueDate() != null ? command.dueDate() : target.dueDate();
        value.put("dueDate", dueDate != null ? dueDate.toString() : null);
        value.put("rejected", rejected);
        return toJson(value, "{}");
    }

    /*
     * few-shot 예시로 예약한다. **세 경우에는 예약하지 않는다.**
     *
     *   REJECT        정답 tuple 이 없다. 반려는 "이건 아니다"이지 "이게 맞다"가 아니므로
     *                 예시로 쓸 값이 없다. 라벨로는 남고 gold set 에는 유효하다
     *   근거 발화 없음  임베딩 대상이 근거 발화 텍스트인데 그게 없다(V5.10). tuple 을 대신
     *                 임베딩하면 쿼리와 키가 다른 공간에 놓여 유사도가 망가진다
     *   수동 추가       AI 입력이 없어 {입력→정답} 쌍이 성립하지 않는다(명세 RVW-03)
     *
     * layer 는 L4 다. 이 예시가 쓰이는 곳이 tuple 추출이기 때문이고, 담당자를 고친 라벨이라도
     * 그렇다 — L1.5 예시는 지시어와 선행사 쌍이라 모양이 다르다.
     */
    private boolean enqueueVector(ReviewDecisionCommand command, ReviewTarget target,
                                  Long assignee, long reviewLogId) {
        if (command.decision() == ReviewDecision.REJECT
                || target.evidenceContent() == null
                || target.evidenceContent().isBlank()
                || target.manual()) {
            return false;
        }

        tupleVectorRepository.enqueue(new VectorEntry(
                command.companyId(),
                command.meetingId(),
                LayerName.L4,
                target.evidenceContent(),
                humanValueJson(command, target, assignee),
                reviewLogId));
        return true;
    }

    /*
     * JSON 직렬화가 실패하면 대체값을 쓴다. **판정 전체를 세우지 않는다** — 사람은 이미
     * 반려를 눌렀고, 여기서 던지면 그 판정이 통째로 롤백된다. 라벨 한 칸이 비는 것보다
     * 판정이 사라지는 것이 나쁘다.
     */
    private String toJson(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            log.warn("라벨 JSON 직렬화에 실패해 빈 값으로 남긴다.", e);
            return fallback;
        }
    }
}
