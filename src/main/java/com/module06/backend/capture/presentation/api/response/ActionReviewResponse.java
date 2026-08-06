package com.module06.backend.capture.presentation.api.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.capture.application.port.out.ActionReviewQueryPort.ReviewAction;
import com.module06.backend.capture.application.result.ActionReview;
import com.module06.backend.capture.domain.model.GateSignals;

/*
 * RVW-01 응답이다. 명세의 필드명을 그대로 따른다.
 *
 * <h2>gate 에 확신도 숫자가 없다</h2>
 * 모델이 스스로 말한 신뢰도는 실제 정확도와 맞지 않는다 — LLM 에 물으면 85~95 에 몰리고
 * 틀린 답에도 높은 숫자를 붙인다. **코드로 판정한 신호를 그대로 노출해 사람이 검증할 수
 * 있게 한다.** 화면 문구가 "AI 확신도 높음"이어도 되지만 백엔드가 퍼센트를 만들어
 * 내려주지는 않는다(명세 RVW-01 처리 정책).
 *
 * <h2>null 을 0·false 로 채우지 않는다</h2>
 * gate 가 null 이면 "게이트를 안 지났다"(사람이 직접 추가한 액션)이고, evidence 가 null 이면
 * "근거 발화가 없다"이다. 기본값으로 채우면 화면이 "게이트가 떨어뜨렸다" · "근거는 있는데
 * 비었다"로 읽고, 둘 다 사람을 엉뚱한 곳으로 보낸다.
 */
public record ActionReviewResponse(
        List<PersonActionsResponse> actionsByPerson,
        NeedsReviewResponse needsReview,
        LocalDateTime dispatchedAt
) {

    public static ActionReviewResponse from(ActionReview review) {
        return new ActionReviewResponse(
                review.actionsByPerson().stream()
                        .map(person -> new PersonActionsResponse(
                                person.memberId(),
                                person.name(),
                                person.actions().stream().map(ActionResponse::from).toList()))
                        .toList(),
                new NeedsReviewResponse(review.needsReview().count(), review.needsReview().actionIds()),
                review.dispatchedAt());
    }

    /*
     * 담당자 한 명과 그 액션들.
     *
     * memberId 가 null 인 묶음이 나올 수 있다 — 담당자 미정이거나 명단 밖을 가리킨 것이다.
     * 화면은 그 묶음을 "담당자 미정"으로 보여주면 된다. 숨기면 안 된다.
     */
    public record PersonActionsResponse(Long memberId, String name, List<ActionResponse> actions) {
    }

    public record ActionResponse(
            Long actionId,
            Long assigneeMemberId,
            String assigneeSource,
            String title,
            String detail,
            LocalDate dueDate,
            String topic,
            boolean isManual,
            String reviewStatus,
            EvidenceResponse evidence,
            GateResponse gate
    ) {

        static ActionResponse from(ReviewAction action) {
            return new ActionResponse(
                    action.actionId(),
                    action.assigneeMemberId(),
                    action.assigneeSource() != null ? action.assigneeSource().name() : null,
                    action.title(),
                    action.detail(),
                    action.dueDate(),
                    action.topic(),
                    action.manual(),
                    action.reviewStatus(),
                    EvidenceResponse.from(action),
                    GateResponse.from(action));
        }
    }

    /*
     * 근거 발화. 검토 화면이 담당자만 보여주고 근거를 감추면 사람이 판단할 재료가 없다.
     *
     * speakerName 이 null 인 것은 정상이다 — L1 이 화자 판정을 포기한 발화다. 화면은 인용만
     * 보여주면 된다. 모르는 이름을 지어내지 않는다.
     */
    public record EvidenceResponse(
            Long transcriptId,
            String speakerName,
            String content,
            Integer startOffsetMs
    ) {

        static EvidenceResponse from(ReviewAction action) {
            if (action.evidence() == null) {
                return null;
            }
            return new EvidenceResponse(
                    action.evidence().transcriptId(),
                    action.evidence().speakerName(),
                    action.evidence().content(),
                    action.evidence().startOffsetMs());
        }
    }

    /*
     * 자동확정 게이트 판정. 신호 넷을 **그대로** 노출한다 — 사람이 "왜 이게 확신도 높음인가"를
     * 검증할 수 있어야 하고, 그게 자기보고 확신도를 쓰지 않는 이유의 나머지 절반이다.
     */
    public record GateResponse(boolean autoConfirmed, SignalsResponse signals) {

        static GateResponse from(ReviewAction action) {
            // 게이트를 안 지난 액션(수동 추가)은 통째로 null 이다. false 로 채우면
            // "게이트가 떨어뜨렸다"와 구분되지 않는다.
            if (action.autoConfirmed() == null || action.signals() == null) {
                return null;
            }
            GateSignals signals = action.signals();
            return new GateResponse(action.autoConfirmed(),
                    new SignalsResponse(
                            signals.hasEvidence(),
                            signals.assigneeInRoster(),
                            /*
                             * 명세는 이 자리에 boolean 이 아니라 판정 근거 값(EXPLICIT_CALL 등)을
                             * 싣는다 — 조건을 만족했는지보다 **어느 경로로 만족했는지**가
                             * 사람에게 더 많은 것을 말해주기 때문이다. 조건 통과 여부는
                             * assigneeSourceOk 가 false 일 때 값이 비는 것으로 드러난다.
                             */
                            action.assigneeSource() != null && signals.assigneeSourceOk()
                                    ? action.assigneeSource().name()
                                    : null,
                            signals.viewsAgree()));
        }
    }

    public record SignalsResponse(
            boolean hasEvidence,
            boolean assigneeInRoster,
            String assigneeSource,
            boolean viewsAgree
    ) {
    }

    public record NeedsReviewResponse(int count, List<Long> actionIds) {
    }
}
