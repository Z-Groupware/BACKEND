package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.ReviewActionDeletePort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort.ReviewAction;
import com.module06.backend.capture.application.usecase.CancelReviewActionUseCase.CancelReviewActionCommand;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RVW-04 · 직접 추가한 액션 취소.
 *
 * <p>검증의 축은 <b>지워지는가</b>가 아니라 <b>지워지면 안 되는 것이 지켜지는가</b>다.
 * AI 생성 액션을 지우면 「AI 가 이런 걸 뽑았고 사람이 아니라고 했다」는 라벨이 사라지고,
 * 지나간 회의는 다시 만들 수 없어 되돌릴 방법이 없다.
 */
class CancelReviewActionServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long ACTION = 8_901L;
    private static final long ME = 99L;

    @Test
    @DisplayName("직접 추가한 액션은 지운다")
    void 수동_추가_액션은_지운다() {
        RecordingDeletePort deletes = new RecordingDeletePort();

        service(query(true), deletes).cancel(command());

        assertThat(deletes.deleted).containsExactly(ACTION);
    }

    @Test
    @DisplayName("AI 생성 액션은 409 로 막는다 — 지우면 라벨이 사라진다")
    void AI_액션은_지우지_않는다() {
        RecordingDeletePort deletes = new RecordingDeletePort();

        assertThatThrownBy(() -> service(query(false), deletes).cancel(command()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_DELETE_AI_ACTION);

        // 포트를 부르지도 않았다 — 판정은 여기서 끝난다.
        assertThat(deletes.deleted).isEmpty();
    }

    @Test
    @DisplayName("그 회의의 액션이 아니면 404 다 — 존재 여부를 흘리지 않는다")
    void 없는_액션은_404다() {
        RecordingDeletePort deletes = new RecordingDeletePort();
        ActionReviewQueryPort empty = new StubQueryPort(null);

        /*
         * 다른 회사 액션도 여기로 온다. 403 을 주면 "그 액션은 존재한다"가 새어 나가고
         * id 를 훑어 남의 회사 액션 개수를 셀 수 있다(#100 과 같은 판단).
         */
        assertThatThrownBy(() -> service(empty, deletes).cancel(command()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_ACTION_NOT_FOUND);
        assertThat(deletes.deleted).isEmpty();
    }

    private CancelReviewActionService service(ActionReviewQueryPort query, RecordingDeletePort deletes) {
        return new CancelReviewActionService(
                query, deletes, new MeetingAccessGuard((companyId, meetingId) -> true));
    }

    private ActionReviewQueryPort query(boolean manual) {
        return new StubQueryPort(new ActionReviewQueryPort.ReviewTarget(
                ACTION, 42L, LocalDate.of(2026, 8, 8), "결제 실패 케이스 정리",
                manual, manual ? "HUMAN_CONFIRMED" : "PENDING", null, null, null, null));
    }

    private CancelReviewActionCommand command() {
        return new CancelReviewActionCommand(COMPANY, MEETING, ACTION, ME);
    }

    private record StubQueryPort(ActionReviewQueryPort.ReviewTarget target)
            implements ActionReviewQueryPort {

        @Override
        public List<ReviewAction> findByMeeting(long companyId, long meetingId, String reviewStatus) {
            return List.of();
        }

        @Override
        public Optional<ReviewTarget> findOne(long companyId, long meetingId, long actionId) {
            return Optional.ofNullable(target);
        }

        // develop HEAD 기준 ActionReviewQueryPort에 이미 있는 계약(RVW-05, dispatchedAtOf) —
        // 이 테스트는 관여하지 않는 경로라 항상 비워서 준다. C 브랜치 한정 임시 보강, review(A)에 별도 통보.
        @Override
        public Optional<LocalDateTime> dispatchedAtOf(long companyId, long meetingId) {
            return Optional.empty();
        }
    }

    /* 무엇을 지웠는지가 검증 대상이다 — 특히 "안 지운 것"이다. */
    private static final class RecordingDeletePort implements ReviewActionDeletePort {

        private final List<Long> deleted = new ArrayList<>();

        @Override
        public void deleteManual(long companyId, long actionId) {
            deleted.add(actionId);
        }
    }
}
