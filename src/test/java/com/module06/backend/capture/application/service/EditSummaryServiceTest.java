package com.module06.backend.capture.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import tools.jackson.databind.ObjectMapper;

import com.module06.backend.capture.application.port.out.MeetingSummaryRepository;
import com.module06.backend.capture.application.port.out.ReviewLogRepository;
import com.module06.backend.capture.application.port.out.ReviewLogRepository.ReviewLogEntry;
import com.module06.backend.capture.application.usecase.EditSummaryUseCase.EditSummaryCommand;
import com.module06.backend.capture.application.usecase.EditSummaryUseCase.ItemEditCommand;
import com.module06.backend.capture.application.usecase.EditSummaryUseCase.SummaryEdited;
import com.module06.backend.capture.domain.model.GateVerdict;
import com.module06.backend.capture.domain.model.ItemType;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.ReviewDecision;
import com.module06.backend.capture.domain.model.ReviewTargetType;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ANLZ-04 · 요약 수정.
 *
 * <p>검증의 축은 <b>라벨이 제대로 남는가</b>다. 문장이 바뀌는 것은 화면에서 바로 보이지만,
 * {AI 가 낸 문장 → 사람이 인정한 문장} 쌍이 잘못 남는 것은 아무도 못 본 채 라벨셋만 오염된다 —
 * 그리고 지나간 회의는 다시 만들 수 없다.
 */
class EditSummaryServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long ME = 99L;
    private static final long ITEM = 41L;

    @Test
    @DisplayName("고친 문장이 반영되고 라벨이 남는다")
    void 수정하면_라벨이_남는다() {
        RecordingReviewLog labels = new RecordingReviewLog();
        FakeSummaryRepo summaries = new FakeSummaryRepo(item(ITEM, "온보딩 3단계", "AI 분류 근거"));

        SummaryEdited edited = service(summaries, labels, true)
                .edit(command(new ItemEditCommand(ITEM, "온보딩 플로우를 2단계로 축소", "사용자 테스트 결과 반영")));

        assertThat(edited.editedCount()).isEqualTo(1);
        assertThat(edited.labelLogged()).isTrue();
        assertThat(edited.editedAt()).isNotNull();
        assertThat(summaries.applied).hasSize(1);

        ReviewLogEntry label = labels.entries.get(0);
        assertThat(label.targetType()).isEqualTo(ReviewTargetType.SUMMARY_ITEM);
        assertThat(label.targetId()).isEqualTo(ITEM);
        // few-shot 조회가 layer 를 필수 필터로 쓴다. 다른 값이면 이 라벨을 아무도 못 찾는다.
        assertThat(label.layer()).isEqualTo(LayerName.L3);
        assertThat(label.decision()).isEqualTo(ReviewDecision.MODIFY);
    }

    @Test
    @DisplayName("요약 수정에는 사유 코드를 요구하지 않는다 — 강제하면 기록이 아예 남지 못한다")
    void 사유_코드_없이_남는다() {
        RecordingReviewLog labels = new RecordingReviewLog();
        FakeSummaryRepo summaries = new FakeSummaryRepo(item(ITEM, "원문", null));

        service(summaries, labels, true).edit(command(new ItemEditCommand(ITEM, "다듬은 문장", null)));

        /*
         * RVW-02 는 MODIFY 에 사유를 422 로 강제하지만 여기는 다르다. V5.9 의 CK_REVIEW_LOG_REASON
         * 이 SUMMARY_ITEM 만 예외로 빼 두었고, 이유가 그 주석에 있다 — 문구만 다듬는 수정에
         * 대응하는 사유 코드가 없어 강제하면 ANLZ-04 기록이 아예 남지 못한다.
         */
        assertThat(labels.entries.get(0).rejectReason()).isNull();
    }

    @Test
    @DisplayName("llm_output 은 고치기 전 값이다 — 고친 값을 넣으면 AI 가 맞힌 것으로 읽힌다")
    void 라벨은_고치기_전과_후를_함께_담는다() {
        RecordingReviewLog labels = new RecordingReviewLog();
        FakeSummaryRepo summaries = new FakeSummaryRepo(item(ITEM, "AI 가 낸 문장", "AI 근거"));

        service(summaries, labels, true).edit(command(new ItemEditCommand(ITEM, "사람이 고친 문장", null)));

        ReviewLogEntry label = labels.entries.get(0);
        assertThat(label.llmOutput()).contains("AI 가 낸 문장");
        assertThat(label.humanValue()).contains("사람이 고친 문장");
        // 라벨의 뜻이 {AI 가 낸 것 → 사람이 고친 것}이라, 둘이 같으면 쌍이 성립하지 않는다.
        assertThat(label.llmOutput()).isNotEqualTo(label.humanValue());
    }

    @Test
    @DisplayName("reason 을 안 보내면 원본 근거를 그대로 담는다 — 지우는 것이 정답이라고 가르치면 안 된다")
    void 안_보낸_reason_은_원본을_유지한다() {
        RecordingReviewLog labels = new RecordingReviewLog();
        FakeSummaryRepo summaries = new FakeSummaryRepo(item(ITEM, "원문", "AI 가 적은 분류 근거"));

        service(summaries, labels, true).edit(command(new ItemEditCommand(ITEM, "고친 문장", null)));

        assertThat(labels.entries.get(0).humanValue()).contains("AI 가 적은 분류 근거");
    }

    @Test
    @DisplayName("다른 회의 항목이 섞이면 아무것도 고치지 않는다")
    void 회의_밖_항목은_404다() {
        RecordingReviewLog labels = new RecordingReviewLog();
        FakeSummaryRepo summaries = new FakeSummaryRepo(item(ITEM, "우리 회의 항목", null));

        /*
         * itemId 는 meeting_decision.id 다. 회의를 함께 걸지 않으면 다른 회사의 요약 문장을
         * 고칠 수 있다(#100 과 같은 성질, 이쪽은 쓰기 경로라 피해가 더 크다).
         */
        assertThatThrownBy(() -> service(summaries, labels, true)
                .edit(command(new ItemEditCommand(ITEM, "고침", null),
                        new ItemEditCommand(9_999L, "남의 회의", null))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .satisfies(code -> {
                    assertThat(code).isEqualTo(CaptureErrorCode.SUMMARY_ITEM_NOT_FOUND);
                    assertThat(code.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        // 일부만 반영하면 사람은 다 됐다고 믿는데 일부가 조용히 빠진다.
        assertThat(summaries.applied).isEmpty();
        assertThat(labels.entries).isEmpty();
    }

    @Test
    @DisplayName("다른 회사 회의면 항목을 읽지도 않는다")
    void 관문이_먼저_선다() {
        RecordingReviewLog labels = new RecordingReviewLog();
        FakeSummaryRepo summaries = new FakeSummaryRepo(item(ITEM, "원문", null));

        assertThatThrownBy(() -> service(summaries, labels, false)
                .edit(command(new ItemEditCommand(ITEM, "고침", null))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.MEETING_NOT_ACCESSIBLE);

        assertThat(summaries.reads).isZero();
    }

    @Test
    @DisplayName("빈 요청은 400 이다 — 200 을 주면 저장됐다고 믿는다")
    void 빈_요청은_막는다() {
        RecordingReviewLog labels = new RecordingReviewLog();
        FakeSummaryRepo summaries = new FakeSummaryRepo(item(ITEM, "원문", null));

        assertThatThrownBy(() -> service(summaries, labels, true)
                .edit(new EditSummaryCommand(COMPANY, MEETING, ME, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.SUMMARY_EDIT_EMPTY);
    }

    @Test
    @DisplayName("항목마다 라벨이 하나씩 남는다")
    void 여러_항목은_각각_라벨이_된다() {
        RecordingReviewLog labels = new RecordingReviewLog();
        FakeSummaryRepo summaries = new FakeSummaryRepo(
                item(41L, "첫 문장", null), item(42L, "둘째 문장", null));

        service(summaries, labels, true).edit(command(
                new ItemEditCommand(41L, "고친 첫 문장", null),
                new ItemEditCommand(42L, "고친 둘째 문장", null)));

        // 한 요청에 묶여 왔어도 라벨은 항목 단위다 — 묶어서 하나로 남기면 어느 문장의 정답인지 모른다.
        assertThat(labels.entries).hasSize(2);
        assertThat(labels.entries).extracting(ReviewLogEntry::targetId).containsExactly(41L, 42L);
    }

    private EditSummaryService service(FakeSummaryRepo summaries, RecordingReviewLog labels, boolean accessible) {
        return new EditSummaryService(summaries, labels,
                new MeetingAccessGuard((companyId, meetingId) -> accessible), new ObjectMapper());
    }

    private static EditSummaryCommand command(ItemEditCommand... items) {
        return new EditSummaryCommand(COMPANY, MEETING, ME, List.of(items));
    }

    private static MeetingSummaryRepository.ItemView item(long id, String content, String reason) {
        return new MeetingSummaryRepository.ItemView(
                id, ItemType.DECISION, content, reason, 8_812L, "CONFIRMED");
    }

    /* 회의 안의 항목만 돌려준다 — 실물의 회사 스코프 성질을 그대로 흉내낸다. */
    private static final class FakeSummaryRepo implements MeetingSummaryRepository {

        private final List<ItemView> items;
        private final List<ItemEdit> applied = new ArrayList<>();
        private int reads;

        private FakeSummaryRepo(ItemView... items) {
            this.items = List.of(items);
        }

        /* 개요 덮어쓰기는 OVERVIEW 계층의 것이다 — ANLZ-04(항목 수정)는 부르지 않는다. */
        @Override
        public boolean replaceOverview(long companyId, long meetingId, String overview,
                                       String modelName, String promptVersion) {
            throw new UnsupportedOperationException("ANLZ-04 는 개요를 덮지 않는다");
        }

        @Override
        public List<ItemView> findItemsInMeeting(long meetingId, List<Long> itemIds) {
            reads++;
            return items.stream().filter(item -> itemIds.contains(item.id())).toList();
        }

        @Override
        public LocalDateTime applyItemEdits(long meetingId, List<ItemEdit> edits, long editorMemberId) {
            applied.addAll(edits);
            return LocalDateTime.of(2026, 8, 9, 15, 20, 11);
        }

        @Override
        public void replace(long companyId, long meetingId, String overview, List<TopicDecisions> topics,
                            String modelName, String promptVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int applyGateVerdicts(long meetingId, List<GateVerdict> verdicts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<MeetingSummaryView> findByMeeting(long companyId, long meetingId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingReviewLog implements ReviewLogRepository {

        private final List<ReviewLogEntry> entries = new ArrayList<>();

        @Override
        public long append(ReviewLogEntry entry) {
            entries.add(entry);
            return entries.size();
        }
    }
}
