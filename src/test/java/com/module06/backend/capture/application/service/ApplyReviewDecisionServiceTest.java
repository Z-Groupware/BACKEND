package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.capture.application.port.out.ActionReviewApplyPort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.ReviewLogRepository;
import com.module06.backend.capture.application.result.ReviewDecisionOutcome;
import com.module06.backend.capture.application.usecase.ApplyReviewDecisionUseCase.ReviewDecisionCommand;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.RejectReason;
import com.module06.backend.capture.domain.model.ReviewDecision;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RVW-02 · 액션 항목 수정·반려.
 *
 * <p><b>라벨이 이 API 의 본체다.</b> 화면에서는 "담당자 바꾸고 저장"이지만, 이 저장소가 그
 * 순간에 얻는 것은 {AI 입력 → 사람이 인정한 정답} 한 쌍이다. 그게 특화 모델의 유일한 연료이고
 * 지나간 회의는 다시 만들 수 없다 — 그래서 여기 검증의 절반이 "무엇이 라벨에 남는가"다.
 */
class ApplyReviewDecisionServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long ACTION = 8821L;
    private static final long ME = 12L;
    private static final long ALICE = 42L;
    private static final long BOB = 43L;

    @Test
    @DisplayName("CONFIRM 도 라벨로 남는다 — 맞힌 것을 안 남기면 라벨셋에 오답만 쌓인다")
    void 무수정_승인도_라벨로_남는다() {
        RecordingReviewLog logs = new RecordingReviewLog();
        RecordingVectorRepository vectors = new RecordingVectorRepository();

        ReviewDecisionOutcome outcome = service(target(), logs, vectors)
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        assertThat(outcome.reviewStatus()).isEqualTo("HUMAN_CONFIRMED");
        assertThat(outcome.labelLogged()).isTrue();
        assertThat(logs.entries).hasSize(1);
        assertThat(logs.entries.get(0).decision()).isEqualTo(ReviewDecision.CONFIRM);
        // 사유 없는 CONFIRM 은 액션을 만든 계층(L4)의 정답 라벨이다. layer 가 없으면
        // few-shot 조회(AI-09)가 이 행을 못 찾는다.
        assertThat(logs.entries.get(0).layer()).isEqualTo(LayerName.L4);
        // human_value 는 null 이다 — llm_output 과 같다는 뜻이고, 같은 값을 두 번 적으면
        // "고쳤는데 우연히 같았다"와 구분되지 않는다.
        assertThat(logs.entries.get(0).humanValue()).isNull();
    }

    @Test
    @DisplayName("CONFIRM 은 few-shot 예시로 예약된다 — AI 가 맞힌 사례가 가장 좋은 예시다")
    void 무수정_승인은_벡터로_예약된다() {
        RecordingVectorRepository vectors = new RecordingVectorRepository();

        ReviewDecisionOutcome outcome = service(target(), new RecordingReviewLog(), vectors)
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        assertThat(outcome.vectorQueued()).isTrue();
        assertThat(vectors.entries).hasSize(1);
        // 임베딩 대상은 근거 발화 원문이다. tuple 을 임베딩하면 검색 쿼리(새 발화)와 키가
        // 다른 공간에 놓여 유사도가 망가진다(V5.10).
        assertThat(vectors.entries.get(0).inputText()).isEqualTo("서준님이 정리해주세요.");
        assertThat(vectors.entries.get(0).layer()).isEqualTo(LayerName.L4);
    }

    @Test
    @DisplayName("담당자를 고치면 action 에 반영되고 사유가 계층을 정한다")
    void 담당자_수정이_반영되고_계층이_기록된다() {
        RecordingApplyPort applied = new RecordingApplyPort();
        RecordingReviewLog logs = new RecordingReviewLog();

        // 2026-08-11 — MODIFY는 rejectReason을 안 받는다. 바뀐 필드(담당자)로 BE가 자동 유도한다.
        service(target(), applied, logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, BOB, null));

        assertThat(applied.assigneeMemberId).isEqualTo(BOB);
        assertThat(applied.reviewStatus).isEqualTo("HUMAN_CONFIRMED");
        assertThat(logs.entries).hasSize(1);
        // WRONG_ASSIGNEE 는 지시어 해소(L1.5)가 사람을 잘못 짚은 것이다 — BE가 자동으로 붙인다.
        assertThat(logs.entries.get(0).rejectReason()).isEqualTo(RejectReason.WRONG_ASSIGNEE);
        assertThat(logs.entries.get(0).layer()).isEqualTo(LayerName.L1_5);
        // 고친 값이 라벨의 정답이다.
        assertThat(logs.entries.get(0).humanValue()).contains("\"assigneeMemberId\":43");
    }

    @Test
    @DisplayName("담당자·기한을 동시에 고치면 review_log가 필드 개수만큼 나뉜다 — 하나로 합치면 정확도 집계가 왜곡된다")
    void 여러_필드_동시_수정은_review_log를_나눈다() {
        RecordingReviewLog logs = new RecordingReviewLog();

        service(target(), logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, BOB, LocalDate.of(2026, 8, 20)));

        assertThat(logs.entries).hasSize(2);
        assertThat(logs.entries.get(0).rejectReason()).isEqualTo(RejectReason.WRONG_ASSIGNEE);
        assertThat(logs.entries.get(0).layer()).isEqualTo(LayerName.L1_5);
        assertThat(logs.entries.get(1).rejectReason()).isEqualTo(RejectReason.WRONG_DUE);
        assertThat(logs.entries.get(1).layer()).isEqualTo(LayerName.L4);
        // 두 행 다 같은 최종 스냅샷(담당자+기한 둘 다)을 담아야 한 행만 보고도 정답을 복원할 수 있다.
        assertThat(logs.entries.get(0).humanValue())
                .contains("\"assigneeMemberId\":43").contains("\"dueDate\":\"2026-08-20\"");
        assertThat(logs.entries.get(1).humanValue())
                .contains("\"assigneeMemberId\":43").contains("\"dueDate\":\"2026-08-20\"");
    }

    @Test
    @DisplayName("제목만 고치면 WRONG_TITLE 하나만 기록된다(2026-08-11 추가)")
    void 제목만_고치면_WRONG_TITLE만_기록된다() {
        RecordingApplyPort applied = new RecordingApplyPort();
        RecordingReviewLog logs = new RecordingReviewLog();

        service(target(), applied, logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, null, null, "새 제목", null));

        assertThat(applied.title).isEqualTo("새 제목");
        assertThat(applied.detail).isNull();
        assertThat(logs.entries).hasSize(1);
        assertThat(logs.entries.get(0).rejectReason()).isEqualTo(RejectReason.WRONG_TITLE);
        assertThat(logs.entries.get(0).layer()).isEqualTo(LayerName.L4);
        assertThat(logs.entries.get(0).humanValue()).contains("\"title\":\"새 제목\"");
    }

    @Test
    @DisplayName("내용만 고치면 WRONG_DETAIL 하나만 기록된다(2026-08-11 추가)")
    void 내용만_고치면_WRONG_DETAIL만_기록된다() {
        RecordingApplyPort applied = new RecordingApplyPort();
        RecordingReviewLog logs = new RecordingReviewLog();

        service(target(), applied, logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, null, null, null, "새 내용"));

        assertThat(applied.detail).isEqualTo("새 내용");
        assertThat(applied.title).isNull();
        assertThat(logs.entries).hasSize(1);
        assertThat(logs.entries.get(0).rejectReason()).isEqualTo(RejectReason.WRONG_DETAIL);
        assertThat(logs.entries.get(0).humanValue()).contains("\"detail\":\"새 내용\"");
    }

    @Test
    @DisplayName("REJECT에 값이 실려 와도 무시한다 — human_value는 target의 현재 값만 담는다(2026-08-11, CodeRabbit 지적)")
    void 반려에_실린_값은_라벨에_안_남는다() {
        RecordingReviewLog logs = new RecordingReviewLog();

        // decision=REJECT인데도 title·detail·dueDate를 함께 보냈다 — apply()는 이 값들을
        // action에 반영하지 않지만, 예전엔 humanValueJson이 이 값을 그대로 라벨에 적었다.
        service(target(), logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.REJECT, RejectReason.HALLUCINATION, BOB,
                        LocalDate.of(2026, 8, 20), "가짜 제목", "가짜 내용"));

        String humanValue = logs.entries.get(0).humanValue();
        // target()의 원래 값(로드맵 초안 작성, 담당자 42, 기한 2026-08-08)만 남아야 한다.
        assertThat(humanValue).contains("\"title\":\"로드맵 초안 작성\"")
                .contains("\"assigneeMemberId\":42")
                .contains("\"dueDate\":\"2026-08-08\"")
                .doesNotContain("가짜 제목").doesNotContain("가짜 내용");
    }

    @Test
    @DisplayName("고치지 않은 칸도 human_value 에 담는다 — 한 행만 보고 정답을 복원할 수 있어야 한다")
    void 고치지_않은_칸은_현재_값으로_채운다() {
        RecordingReviewLog logs = new RecordingReviewLog();

        // 기한만 고쳤다. 담당자는 그대로다.
        service(target(), logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, null,
                        LocalDate.of(2026, 8, 20)));

        String humanValue = logs.entries.get(0).humanValue();
        assertThat(humanValue).contains("\"dueDate\":\"2026-08-20\"")
                // 안 고친 담당자도 담긴다 — 바뀐 칸만 담으면 다른 표를 다시 읽어야 정답이 된다.
                .contains("\"assigneeMemberId\":42");
    }

    @Test
    @DisplayName("반려는 값을 고치지 않고 상태만 바꾼다 — llm_output 과 action 이 갈리면 안 된다")
    void 반려는_값을_고치지_않는다() {
        RecordingApplyPort applied = new RecordingApplyPort();

        ReviewDecisionOutcome outcome = service(target(), applied, new RecordingReviewLog(),
                new RecordingVectorRepository())
                .apply(command(ReviewDecision.REJECT, RejectReason.DUPLICATE, BOB,
                        LocalDate.of(2026, 8, 20)));

        assertThat(outcome.reviewStatus()).isEqualTo("REJECTED");
        // 담당자·기한을 함께 보내와도 무시한다.
        assertThat(applied.assigneeMemberId).isNull();
        assertThat(applied.dueDate).isNull();
    }

    @Test
    @DisplayName("반려는 few-shot 예시로 예약하지 않는다 — '이건 아니다'는 정답 tuple 이 아니다")
    void 반려는_벡터로_예약하지_않는다() {
        RecordingVectorRepository vectors = new RecordingVectorRepository();

        ReviewDecisionOutcome outcome = service(target(), new RecordingReviewLog(), vectors)
                .apply(command(ReviewDecision.REJECT, RejectReason.HALLUCINATION, null, null));

        // 라벨은 남는다(gold set 으로는 유효하다). 예시로만 쓰지 않는다.
        assertThat(outcome.labelLogged()).isTrue();
        assertThat(outcome.vectorQueued()).isFalse();
        assertThat(vectors.entries).isEmpty();
    }

    @Test
    @DisplayName("반려에 사유가 없으면 422 — 사유 없는 라벨은 어느 계층을 고칠지 못 가리킨다")
    void 사유가_없는_반려는_거절한다() {
        assertThatThrownBy(() -> service(target(), new RecordingReviewLog(), new RecordingVectorRepository())
                .apply(command(ReviewDecision.REJECT, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_REASON_REQUIRED);
    }

    @Test
    @DisplayName("MODIFY인데 고친 값이 하나도 없으면 422 — 뭘 고쳤다는 건지 알 수 없다(2026-08-11 추가)")
    void 값이_없는_수정은_거절한다() {
        assertThatThrownBy(() -> service(target(), new RecordingReviewLog(), new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_MODIFY_VALUE_REQUIRED);
    }

    // ── 예정 시작일 (plannedStartDate · #386 후속) ────────────────────────────────

    @Test
    @DisplayName("⚠ CONFIRM 에 예정 시작일은 실을 수 있다 — AI 가 낸 값이 아니라 사람이 처음 정하는 값이다")
    void 확정에_예정_시작일은_실을_수_있다() {
        RecordingApplyPort applied = new RecordingApplyPort();
        LocalDate planned = LocalDate.of(2026, 8, 20);

        service(target(), applied, new RecordingReviewLog(), new RecordingVectorRepository())
                .apply(command(ReviewDecision.CONFIRM, null, null, null, null, null, planned));

        /*
         * 다른 넷은 CONFIRM 에 실리면 422 다("AI 값이 맞다"와 "고쳤다"가 모순이므로).
         * 예정 시작일은 AI 가 내지 않는 값이라 고칠 대상이 없다 — "AI 값은 다 맞으니 확정하고,
         * 시작일만 정해 둔다"가 자연스러운 조합이고, 막으면 화면이 확정 직후 별도 요청을
         * 한 번 더 보내야 한다.
         */
        assertThat(applied.plannedStartDate).isEqualTo(planned);
        assertThat(applied.reviewStatus).isEqualTo("HUMAN_CONFIRMED");
    }

    @Test
    @DisplayName("⚠ 예정 시작일만 고친 MODIFY 는 라벨을 남기지 않는다 — 사유 없는 ACTION MODIFY 는 DB 가 거절한다")
    void 예정_시작일만_고치면_라벨이_없다() {
        RecordingReviewLog logs = new RecordingReviewLog();
        RecordingVectorRepository vectors = new RecordingVectorRepository();

        service(target(), logs, vectors)
                .apply(command(ReviewDecision.MODIFY, null, null, null, null, null,
                        LocalDate.of(2026, 8, 20)));

        /*
         * 사유 없이 한 건 남기면 V5.9 의 CK_REVIEW_LOG_REASON 이 막는다 — ACTION + MODIFY 는
         * 사유가 NOT NULL 이어야 한다(SUMMARY_ITEM 만 예외). 위반하면 **판정 트랜잭션 전체가
         * 롤백돼** 시작일 하나 때문에 확정이 실패한다(CodeRabbit PR #422 지적).
         *
         * 제약을 넓히지 않고 라벨을 안 만드는 쪽을 골랐다. 예정 시작일은 AI 가 내지 않는
         * 값이라 {AI 입력 → 정답} 쌍이 성립하지 않는다 — 학습할 것이 없는 행이다.
         */
        assertThat(logs.entries).isEmpty();
        // 가리킬 라벨이 없으므로 few-shot 예시도 예약하지 않는다.
        assertThat(vectors.entries).isEmpty();
    }

    @Test
    @DisplayName("다른 필드와 함께 오면 그 필드들의 라벨만 남는다 — 예정 시작일은 사유를 더하지 않는다")
    void 예정_시작일은_사유를_더하지_않는다() {
        RecordingReviewLog logs = new RecordingReviewLog();

        service(target(), logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, BOB, null, null, null,
                        LocalDate.of(2026, 8, 20)));

        // 담당자 하나만 고쳤으므로 라벨도 하나다. 시작일이 함께 와도 늘지 않는다.
        assertThat(logs.entries).hasSize(1);
        assertThat(logs.entries.get(0).rejectReason()).isEqualTo(RejectReason.WRONG_ASSIGNEE);
    }

    @Test
    @DisplayName("예정 시작일만 보낸 MODIFY 는 통과한다 — 뭘 고쳤는지 분명하다")
    void 예정_시작일만_보낸_수정은_통과한다() {
        RecordingApplyPort applied = new RecordingApplyPort();

        service(target(), applied, new RecordingReviewLog(), new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, null, null, null, null,
                        LocalDate.of(2026, 8, 20)));

        // REVIEW_MODIFY_VALUE_REQUIRED 가 막는 것은 빈 요청이고, 이건 빈 요청이 아니다.
        assertThat(applied.called).isTrue();
    }

    @Test
    @DisplayName("반려는 예정 시작일도 반영하지 않는다 — 반려는 상태만 바꾼다")
    void 반려는_예정_시작일도_무시한다() {
        RecordingApplyPort applied = new RecordingApplyPort();

        service(target(), applied, new RecordingReviewLog(), new RecordingVectorRepository())
                .apply(command(ReviewDecision.REJECT, RejectReason.NOT_ACTION, null, null, null, null,
                        LocalDate.of(2026, 8, 20)));

        /*
         * 반려된 액션의 값을 바꾸면 라벨의 llm_output 과 action 이 갈리고 "AI 가 무엇을
         * 냈는지"를 화면에서 되짚을 수 없다. 예정 시작일도 같은 규칙을 따른다.
         */
        assertThat(applied.plannedStartDate).isNull();
        assertThat(applied.reviewStatus).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("REJECT에 MODIFY 전용 사유(WRONG_*)를 보내면 422 — 반려 사유와 수정 사유는 섞이면 안 된다(2026-08-11 추가)")
    void 반려에_수정_전용_사유는_거절한다() {
        assertThatThrownBy(() -> service(target(), new RecordingReviewLog(), new RecordingVectorRepository())
                .apply(command(ReviewDecision.REJECT, RejectReason.WRONG_ASSIGNEE, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_REASON_NOT_SELECTABLE);
    }

    @Test
    @DisplayName("CONFIRM 에 사유가 붙으면 422 — '맞혔는데 틀렸다'는 모순이다")
    void 사유가_붙은_승인은_거절한다() {
        assertThatThrownBy(() -> service(target(), new RecordingReviewLog(), new RecordingVectorRepository())
                .apply(command(ReviewDecision.CONFIRM, RejectReason.DUPLICATE, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("CONFIRM 에 담당자가 붙으면 422 — 액션만 바뀌고 라벨에는 안 남는 것을 막는다")
    void 값이_붙은_승인은_거절한다() {
        RecordingApplyPort applied = new RecordingApplyPort();
        RecordingReviewLog logs = new RecordingReviewLog();

        assertThatThrownBy(() -> service(target(), applied, logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.CONFIRM, null, BOB, null)))
                .isInstanceOf(BusinessException.class);

        // CONFIRM 의 human_value 는 null 이라, 값을 반영하면 액션은 바뀌는데 라벨에는 그 변경이
        // 남지 않는다 — 나중에 "AI 가 맞혔다"로 읽히지만 정답은 사람이 고친 값이다.
        assertThat(applied.called).isFalse();
        assertThat(logs.entries).isEmpty();
    }

    @Test
    @DisplayName("CONFIRM 에 기한이 붙어도 422 — 값을 조용히 버리면 화면과 DB 가 갈린다")
    void 기한이_붙은_승인도_거절한다() {
        RecordingApplyPort applied = new RecordingApplyPort();

        assertThatThrownBy(() -> service(target(), applied, new RecordingReviewLog(),
                new RecordingVectorRepository())
                .apply(command(ReviewDecision.CONFIRM, null, null, LocalDate.of(2026, 8, 20))))
                .isInstanceOf(BusinessException.class);

        assertThat(applied.called).isFalse();
    }

    @Test
    @DisplayName("참석자 명단 밖 담당자는 422 — 틀린 배정을 정답 라벨로 학습시키면 안 된다")
    void 명단_밖_담당자는_거절한다() {
        RecordingApplyPort applied = new RecordingApplyPort();

        assertThatThrownBy(() -> service(target(), applied, new RecordingReviewLog(),
                new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, 999L, null)))
                .isInstanceOf(BusinessException.class);

        // action 을 건드리기 전에 막는다.
        assertThat(applied.called).isFalse();
    }

    @Test
    @DisplayName("담당자 없는 액션의 CONFIRM 은 422 — 담당자 null 이 정답 라벨로 학습된다")
    void 담당자_없는_액션은_승인할_수_없다() {
        RecordingApplyPort applied = new RecordingApplyPort();
        RecordingReviewLog logs = new RecordingReviewLog();
        RecordingVectorRepository vectors = new RecordingVectorRepository();

        assertThatThrownBy(() -> service(unassignedTarget(), applied, logs, vectors)
                .apply(command(ReviewDecision.CONFIRM, null, null, null)))
                .isInstanceOf(BusinessException.class);

        // 라벨도 예시도 남기지 않는다. 남으면 "담당자를 비우는 것이 정답"이 few-shot 으로 간다.
        assertThat(applied.called).isFalse();
        assertThat(logs.entries).isEmpty();
        assertThat(vectors.entries).isEmpty();
    }

    @Test
    @DisplayName("담당자를 안 채운 MODIFY 도 422 — 기한만 고치고 확정으로 넘어가는 길을 막는다")
    void 담당자를_채우지_않은_수정도_거절한다() {
        RecordingApplyPort applied = new RecordingApplyPort();

        assertThatThrownBy(() -> service(unassignedTarget(), applied, new RecordingReviewLog(),
                new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, null,
                        LocalDate.of(2026, 8, 20))))
                .isInstanceOf(BusinessException.class);

        assertThat(applied.called).isFalse();
    }

    @Test
    @DisplayName("담당자를 채워 보낸 MODIFY 는 지나간다 — 막는 것은 '안 채운 확정'뿐이다")
    void 담당자를_채운_수정은_통과한다() {
        RecordingApplyPort applied = new RecordingApplyPort();
        RecordingReviewLog logs = new RecordingReviewLog();

        service(unassignedTarget(), applied, logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, BOB, null));

        assertThat(applied.assigneeMemberId).isEqualTo(BOB);
        assertThat(applied.reviewStatus).isEqualTo("HUMAN_CONFIRMED");
        // 이번 요청의 담당자를 본다 — 액션의 옛 값(null)으로 판정하면 채워 보낸 수정이 거절된다.
        assertThat(logs.entries.get(0).humanValue()).contains("\"assigneeMemberId\":43");
    }

    @Test
    @DisplayName("담당자 없는 액션도 반려는 된다 — 담당자를 못 정해 버리는 길이 반려다")
    void 담당자_없는_액션도_반려할_수_있다() {
        RecordingApplyPort applied = new RecordingApplyPort();

        ReviewDecisionOutcome outcome = service(unassignedTarget(), applied, new RecordingReviewLog(),
                new RecordingVectorRepository())
                .apply(command(ReviewDecision.REJECT, RejectReason.HALLUCINATION, null, null));

        assertThat(outcome.reviewStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("TEAM 액션은 담당자 없이도 확정된다 — 담당자 개념 자체가 없다")
    void 팀_액션은_담당자_없이_승인된다() {
        RecordingApplyPort applied = new RecordingApplyPort();

        ReviewDecisionOutcome outcome = service(teamTarget(), applied, new RecordingReviewLog(),
                new RecordingVectorRepository())
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        // 함께 막으면 팀 액션은 영원히 확정되지 않는다.
        assertThat(outcome.reviewStatus()).isEqualTo("HUMAN_CONFIRMED");
        assertThat(applied.called).isTrue();
    }

    @Test
    @DisplayName("그 회의의 액션이 아니면 404 — 회의 관문은 actionId 를 보지 않는다")
    void 없는_액션은_404() {
        assertThatThrownBy(() -> new ApplyReviewDecisionService(
                new FakeQueryPort(null), new RecordingApplyPort(), new RecordingReviewLog(),
                new RecordingVectorRepository(), accessibleGuard(), roster(), new ObjectMapper())
                .apply(command(ReviewDecision.CONFIRM, null, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("다른 회사 회의는 관문에서 막는다 — 조회조차 하지 않는다")
    void 다른_회사_회의는_막는다() {
        FakeQueryPort port = new FakeQueryPort(target());

        assertThatThrownBy(() -> new ApplyReviewDecisionService(
                port, new RecordingApplyPort(), new RecordingReviewLog(),
                new RecordingVectorRepository(),
                new MeetingAccessGuard((companyId, meetingId) -> false), roster(), new ObjectMapper())
                .apply(command(ReviewDecision.CONFIRM, null, null, null)))
                .isInstanceOf(BusinessException.class);

        assertThat(port.called).isFalse();
    }

    @Test
    @DisplayName("수동 추가 액션은 예시로 예약하지 않는다 — AI 입력이 없어 {입력→정답} 쌍이 없다")
    void 수동_추가_액션은_벡터로_예약하지_않는다() {
        ActionReviewQueryPort.ReviewTarget manual = new ActionReviewQueryPort.ReviewTarget(
                ACTION, ActionType.PERSONAL, ALICE, LocalDate.of(2026, 8, 8), "직접 추가한 일",
                "직접 추가한 내용", true, "PENDING", 8812L, "서준님이 정리해주세요.", null, null);
        RecordingReviewLog logs = new RecordingReviewLog();
        RecordingVectorRepository vectors = new RecordingVectorRepository();

        ReviewDecisionOutcome outcome = service(manual, logs, vectors)
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        assertThat(outcome.vectorQueued()).isFalse();
        // 라벨은 남고 is_manual 로 표시된다 — gold set 라벨로는 유효하다.
        assertThat(logs.entries.get(0).manual()).isTrue();
        // AI 원본이 없으므로 llm_output 은 빈 객체다. null 을 넣을 수 없는 컬럼이고,
        // is_manual 이 그 빈 값의 뜻을 함께 말해준다.
        assertThat(logs.entries.get(0).llmOutput()).isEqualTo("{}");
        assertThat(logs.entries.get(0).modelName()).isNull();
    }

    @Test
    @DisplayName("근거 발화가 없으면 예시로 예약하지 않는다 — 임베딩할 텍스트가 없다")
    void 근거가_없으면_벡터로_예약하지_않는다() {
        ActionReviewQueryPort.ReviewTarget noEvidence = new ActionReviewQueryPort.ReviewTarget(
                ACTION, ActionType.PERSONAL, ALICE, LocalDate.of(2026, 8, 8), "로드맵 초안 작성",
                "초안을 작성한다", false, "PENDING", null, null, "제품 로드맵", aiValue());

        ReviewDecisionOutcome outcome = service(noEvidence, new RecordingReviewLog(),
                new RecordingVectorRepository())
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        assertThat(outcome.labelLogged()).isTrue();
        assertThat(outcome.vectorQueued()).isFalse();
    }

    @Test
    @DisplayName("input_context 에 참석자 명단과 근거 발화를 담는다 — 재현할 수 없는 값이다")
    void 재현할_수_없는_값을_라벨에_담는다() {
        RecordingReviewLog logs = new RecordingReviewLog();

        service(target(), logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        String context = logs.entries.get(0).inputContext();
        assertThat(context).contains("\"roster\"")
                .contains("김서준")
                .contains("서준님이 정리해주세요.")
                // few-shot 을 쓴 적이 없다는 사실도 값으로 남긴다 — 나중에 켠 뒤 비교할 때
                // 이 구분이 필요하다.
                .contains("\"usedFewShot\":false");
        // 모델·프롬프트 버전은 tuple 에서 온다. 어느 버전이 틀렸는지 모르면 개선 여부를
        // 비교할 수 없다.
        assertThat(logs.entries.get(0).modelName()).isEqualTo("gemini-2.5-flash");
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private ApplyReviewDecisionService service(ActionReviewQueryPort.ReviewTarget target,
                                               RecordingReviewLog logs,
                                               RecordingVectorRepository vectors) {
        return service(target, new RecordingApplyPort(), logs, vectors);
    }

    private ApplyReviewDecisionService service(ActionReviewQueryPort.ReviewTarget target,
                                               RecordingApplyPort applied,
                                               RecordingReviewLog logs,
                                               RecordingVectorRepository vectors) {
        return new ApplyReviewDecisionService(
                new FakeQueryPort(target), applied, logs, vectors,
                accessibleGuard(), roster(), new ObjectMapper());
    }

    private static MeetingAccessGuard accessibleGuard() {
        return new MeetingAccessGuard((companyId, meetingId) -> true);
    }

    /* 참석자 명단. 명단 밖 탈출구(personId=null)를 포함한다 — 실제 명단의 모양이다. */
    private static MeetingParticipantProvider roster() {
        return meetingId -> List.of(
                new AiLayerPort.Participant(ALICE, "김서준"),
                new AiLayerPort.Participant(BOB, "박도현"),
                new AiLayerPort.Participant(null, "명단 외"));
    }

    private static ReviewDecisionCommand command(ReviewDecision decision, RejectReason reason,
                                                 Long assignee, LocalDate dueDate) {
        return command(decision, reason, assignee, dueDate, null, null);
    }

    private static ReviewDecisionCommand command(ReviewDecision decision, RejectReason reason,
                                                 Long assignee, LocalDate dueDate, String title, String detail) {
        return command(decision, reason, assignee, dueDate, title, detail, null);
    }

    /* 예정 시작일까지 담는 조립. 그 값을 보는 테스트만 이걸 직접 쓴다. */
    private static ReviewDecisionCommand command(ReviewDecision decision, RejectReason reason,
                                                 Long assignee, LocalDate dueDate, String title, String detail,
                                                 LocalDate plannedStartDate) {
        return new ReviewDecisionCommand(
                COMPANY, MEETING, ACTION, ME, decision, reason, assignee, dueDate, title, detail, plannedStartDate);
    }

    private static ActionReviewQueryPort.ReviewTarget target() {
        return new ActionReviewQueryPort.ReviewTarget(
                ACTION, ActionType.PERSONAL, ALICE, LocalDate.of(2026, 8, 8), "로드맵 초안 작성",
                "초안을 작성한다", false, "PENDING", 8812L, "서준님이 정리해주세요.", "제품 로드맵", aiValue());
    }

    /* AI 가 담당자를 못 정한 액션. 분배는 이 상태를 허용하고, 채우는 자리가 검토 화면이다. */
    private static ActionReviewQueryPort.ReviewTarget unassignedTarget() {
        return new ActionReviewQueryPort.ReviewTarget(
                ACTION, ActionType.PERSONAL, null, LocalDate.of(2026, 8, 8), "로드맵 초안 작성",
                "초안을 작성한다", false, "PENDING", 8812L, "서준님이 정리해주세요.", "제품 로드맵", aiValue());
    }

    /* TEAM 액션은 담당자 개념이 없다 — 팀 전체가 대상이다(ActionTypeShapePolicy). */
    private static ActionReviewQueryPort.ReviewTarget teamTarget() {
        return new ActionReviewQueryPort.ReviewTarget(
                ACTION, ActionType.TEAM, null, LocalDate.of(2026, 8, 8), "팀 회고 준비",
                "회고 준비", false, "PENDING", 8812L, "서준님이 정리해주세요.", "제품 로드맵", aiValue());
    }

    private static ActionReviewQueryPort.AiValue aiValue() {
        return new ActionReviewQueryPort.AiValue(
                "로드맵 초안 작성", null, ALICE, AssigneeSource.EXPLICIT_CALL,
                LocalDate.of(2026, 8, 8), "gemini-2.5-flash", "v1");
    }

    // ── 가짜 구현 ───────────────────────────────────────────────────────────────

    private static final class FakeQueryPort implements ActionReviewQueryPort {

        private final ReviewTarget target;
        private boolean called;

        private FakeQueryPort(ReviewTarget target) {
            this.target = target;
        }

        @Override
        public Optional<ReviewTarget> findOne(long companyId, long meetingId, long actionId) {
            called = true;
            return Optional.ofNullable(target);
        }

        @Override
        public List<ReviewAction> findByMeeting(long companyId, long meetingId, String reviewStatus) {
            throw new UnsupportedOperationException();
        }

        /* RVW-02 는 분배 시각을 보지 않는다 — 판정은 확정 전에도 뒤에도 할 수 있다. */
        @Override
        public Optional<java.time.LocalDateTime> dispatchedAtOf(long companyId, long meetingId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingApplyPort implements ActionReviewApplyPort {

        private boolean called;
        private Long assigneeMemberId;
        private LocalDate dueDate;
        private String title;
        private String detail;
        private LocalDate plannedStartDate;
        private String reviewStatus;

        @Override
        public void apply(long companyId, long actionId, Long assigneeMemberId,
                          LocalDate dueDate, String title, String detail,
                          LocalDate plannedStartDate, String reviewStatus) {
            this.called = true;
            this.assigneeMemberId = assigneeMemberId;
            this.dueDate = dueDate;
            this.title = title;
            this.detail = detail;
            this.plannedStartDate = plannedStartDate;
            this.reviewStatus = reviewStatus;
        }
    }

    private static final class RecordingReviewLog implements ReviewLogRepository {

        private final List<ReviewLogEntry> entries = new ArrayList<>();

        @Override
        public long append(ReviewLogEntry entry) {
            entries.add(entry);
            return 900L + entries.size();
        }
    }

    private static final class RecordingVectorRepository
            implements com.module06.backend.capture.application.port.out.TupleVectorRepository {

        private final List<VectorEntry> entries = new ArrayList<>();

        @Override
        public void enqueue(VectorEntry entry) {
            entries.add(entry);
        }

        /* 인덱스 반영은 워커(TupleVectorSyncService)의 몫이다 — RVW-02 는 예약까지만 한다. */
        @Override
        public List<PendingVector> findPending(int maxAttempts, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markSynced(long id, String pointId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markSyncFailed(long id) {
            throw new UnsupportedOperationException();
        }
    }
}
