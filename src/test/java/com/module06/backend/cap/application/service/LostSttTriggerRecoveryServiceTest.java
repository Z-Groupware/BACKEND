package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.port.out.MeetingRecordingSttPort;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.LostSttTriggerRepository;

/*
 * 유실된 STT 트리거 복구의 계약을 고정한다(#574).
 *
 * 가장 중요한 검증은 **조회 범위**다. 유예가 없으면 방금 등록된 녹음을 다시 제출해
 * UNIQUE(provider_job_name) 위반이 나고, 상한이 없으면 영구 실패를 영원히 다시 부른다.
 * 둘 다 조용히 깨지는 종류라 테스트로 박아 둔다.
 */
class LostSttTriggerRecoveryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 17, 12, 0, 0);

    @Test
    @DisplayName("유실된 녹음을 원래 회의·원래 키로 다시 건다")
    void 유실된_녹음을_다시_건다() {
        RecordingLostTriggers lost = new RecordingLostTriggers(
                recording(41L, "recordings/org-11/member-9/online-pending/uuid/a.m4a"));
        RecordingSttPort port = new RecordingSttPort();

        assertThat(service(lost, port).recoverOnce()).isEqualTo(1);

        assertThat(port.calls).singleElement().satisfies(call -> {
            assertThat(call.meetingId()).isEqualTo(41L);
            assertThat(call.s3Key()).isEqualTo("recordings/org-11/member-9/online-pending/uuid/a.m4a");
        });
    }

    @Test
    @DisplayName("⚠ 유예보다 새 녹음은 조회하지 않는다 — 방금 건 트리거를 다시 걸면 잡 이름이 충돌한다")
    void 유예를_조회_범위에_적용한다() {
        RecordingLostTriggers lost = new RecordingLostTriggers();

        service(lost, new RecordingSttPort()).recoverOnce();

        // 트리거 직후에도 stt_block 은 잠깐 0건이다 — 그 구간을 주우면 이중 제출이 된다.
        assertThat(lost.createdUntil).isEqualTo(NOW.minus(LostSttTriggerRecoveryService.GRACE));
    }

    @Test
    @DisplayName("⚠ 상한보다 오래된 녹음도 조회하지 않는다 — 재시도 횟수 컬럼이 없어 나이가 그 자리를 대신한다")
    void 재시도_상한을_조회_범위에_적용한다() {
        RecordingLostTriggers lost = new RecordingLostTriggers();

        service(lost, new RecordingSttPort()).recoverOnce();

        assertThat(lost.createdFrom).isEqualTo(NOW.minus(LostSttTriggerRecoveryService.RETRY_CEILING));
    }

    @Test
    @DisplayName("한 주기 상한을 넘겨 가져오지 않는다 — 밀린 물량을 몰아 태우면 정상 업로드가 한도에 걸린다")
    void 한_주기_상한을_넘기지_않는다() {
        RecordingLostTriggers lost = new RecordingLostTriggers();

        service(lost, new RecordingSttPort()).recoverOnce();

        assertThat(lost.limit).isEqualTo(LostSttTriggerRecoveryService.MAX_PER_CYCLE);
    }

    @Test
    @DisplayName("한 건이 터져도 나머지를 계속 돈다 — 첫 건이 뒤를 막으면 이 배치가 고치려던 사고와 같아진다")
    void 한_건이_실패해도_나머지를_돈다() {
        RecordingLostTriggers lost = new RecordingLostTriggers(
                recording(51L, "a.m4a"), recording(52L, "b.m4a"), recording(53L, "c.m4a"));
        RecordingSttPort port = new RecordingSttPort(52L);

        // 실패한 52는 세지 않는다 — 성공 건수가 곧 "실제로 복구된 것"이어야 한다.
        assertThat(service(lost, port).recoverOnce()).isEqualTo(2);

        assertThat(port.calls).extracting(Call::meetingId).containsExactly(51L, 52L, 53L);
    }

    @Test
    @DisplayName("주울 것이 없으면 0을 답하고 제출을 부르지 않는다")
    void 후보가_없으면_아무것도_하지_않는다() {
        RecordingSttPort port = new RecordingSttPort();

        assertThat(service(new RecordingLostTriggers(), port).recoverOnce()).isZero();

        assertThat(port.calls).isEmpty();
    }

    private LostSttTriggerRecoveryService service(LostSttTriggerRepository lost, MeetingRecordingSttPort port) {
        return new LostSttTriggerRecoveryService(lost, port,
                Clock.fixed(NOW.atZone(KST).toInstant(), KST));
    }

    private static Recording recording(long meetingId, String s3Key) {
        return Recording.restore(meetingId, meetingId, "a.m4a", s3Key, 1L, null, true,
                NOW.minusHours(1), NOW.minusHours(1));
    }

    /* 무엇을 어떤 범위로 물었는지가 검증 대상이다 — 범위가 틀리면 조용히 깨진다. */
    private static final class RecordingLostTriggers implements LostSttTriggerRepository {

        private final List<Recording> found;
        private LocalDateTime createdFrom;
        private LocalDateTime createdUntil;
        private int limit;

        private RecordingLostTriggers(Recording... found) {
            this.found = List.of(found);
        }

        @Override
        public List<Recording> findSttTriggeredWithoutBlocks(LocalDateTime createdFrom, LocalDateTime createdUntil,
                                                             int limit) {
            this.createdFrom = createdFrom;
            this.createdUntil = createdUntil;
            this.limit = limit;
            return found;
        }
    }

    /* 지정한 회의에서만 던진다 — 나머지가 계속 도는지를 보기 위함이다. */
    private static final class RecordingSttPort implements MeetingRecordingSttPort {

        private final List<Call> calls = new ArrayList<>();
        private final Long explodingMeetingId;

        private RecordingSttPort() {
            this(null);
        }

        private RecordingSttPort(Long explodingMeetingId) {
            this.explodingMeetingId = explodingMeetingId;
        }

        @Override
        public void triggerWholeFileStt(Long meetingId, String s3Key) {
            calls.add(new Call(meetingId, s3Key));
            if (explodingMeetingId != null && explodingMeetingId.equals(meetingId)) {
                throw new IllegalStateException("제출이 400 으로 거절됐다");
            }
        }
    }

    private record Call(Long meetingId, String s3Key) {
    }
}
