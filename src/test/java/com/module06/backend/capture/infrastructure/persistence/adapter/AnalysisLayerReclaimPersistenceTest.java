package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository.LayerState;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository.LockResult;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.application.result.ProcessingStatus;
import com.module06.backend.capture.application.result.ProcessingStatus.LayerProgress;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * #177 · 종료·크래시로 남은 RUNNING 계층을 되찾는다.
 *
 * 잠금이 **살아 있는 실행과 죽은 실행을 구분하는가**가 검증의 전부다. 두 방향으로 틀릴 수 있고
 * 대가가 다르다 —
 *   회수를 못 하면  그 회의는 영원히 분석되지 않는다(이 이슈의 증상)
 *   너무 회수하면  살아서 도는 분석을 죽었다고 보고 같은 회의를 두 번 태운다(#134 가 막던 상태)
 * 뒤쪽이 더 나쁘므로 「살아 있는 실행은 회수되지 않는다」가 이 파일의 중심 테스트다.
 *
 * ⚠ 실물 어댑터를 쓴다. 회수 판정이 **DB 에 저장된 시각** 위에서 도는 것이 주장의 내용이라
 * 가짜 저장소로는 검증되지 않는다(AnalysisRunOrderingPersistenceTest 와 같은 판단).
 *
 * 시간을 앞으로 돌리는 대신 **행의 시각을 과거로 미룬다.** Clock 빈은 프로젝트에 하나뿐이고
 * (MeetingTimeConfiguration#meetingClock) 그걸 테스트에서 갈아 끼우면 meeting 도메인이 함께
 * 흔들린다. 심장이 멈춘 상태는 "마지막 박동이 오래됐다"가 전부이므로 그 값만 미루면 된다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:layerreclaimdb;MODE=MySQL;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"
})
@DisplayName("#177 멈춘 계층 잠금 회수")
class AnalysisLayerReclaimPersistenceTest {

    @Autowired
    private AnalysisLayerRepository analysisLayerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("살아 있는 실행의 계층은 회수되지 않는다 — 회수하면 같은 회의를 두 번 태운다")
    void 살아있는_계층은_회수하지_않는다() {
        long meetingId = 9_301L;

        assertThat(lock(meetingId, LayerName.L2)).isEqualTo(LockResult.ACQUIRED);

        // 방금 잡았으므로 심장이 뛰고 있다. 지금까지와 같이 중복으로 걸러져야 한다.
        assertThat(lock(meetingId, LayerName.L2)).isEqualTo(LockResult.ALREADY_RUNNING);
        assertThat(stateOf(meetingId, LayerName.L2).stalled()).isFalse();
    }

    @Test
    @DisplayName("심장이 멈춘 계층은 다음 실행이 회수한다 — 이 경로가 없으면 그 회의는 끝난다")
    void 멈춘_계층은_회수된다() {
        long meetingId = 9_302L;
        lock(meetingId, LayerName.L3);

        // 잠근 프로세스가 죽어 심장이 멈춘 상태.
        stopHeartbeat(meetingId, LayerName.L3, 10);

        assertThat(lock(meetingId, LayerName.L3)).isEqualTo(LockResult.ACQUIRED);
        // 회수는 재실행이다 — 시도 횟수가 오르고 심장이 다시 뛴다.
        assertThat(attemptCountOf(meetingId, LayerName.L3)).isEqualTo(2);
        assertThat(stateOf(meetingId, LayerName.L3).stalled()).isFalse();
    }

    @Test
    @DisplayName("오래 걸리는 계층이어도 심장이 뛰면 회수되지 않는다 — L5 가 이 자리다")
    void 심장이_뛰는_동안은_회수되지_않는다() {
        long meetingId = 9_303L;
        int attempt = lockAttempt(meetingId, LayerName.L5);

        // 시작한 지는 오래됐다(tuple 이 많은 회의). 하지만 아직 돌고 있다.
        stopHeartbeat(meetingId, LayerName.L5, 30);
        analysisLayerRepository.heartbeat(meetingId, LayerName.L5, attempt);

        assertThat(lock(meetingId, LayerName.L5)).isEqualTo(LockResult.ALREADY_RUNNING);
        assertThat(attemptCountOf(meetingId, LayerName.L5)).isEqualTo(1);
    }

    @Test
    @DisplayName("멈춘 계층은 화면에서도 「처리 중」이 아니다 — 아니면 ANLZ-01 이 409 로 막는다")
    void 멈춘_계층은_처리중으로_보이지_않는다() {
        long meetingId = 9_304L;
        lock(meetingId, LayerName.L4);
        stopHeartbeat(meetingId, LayerName.L4, 10);

        LayerState state = stateOf(meetingId, LayerName.L4);
        // 저장된 값은 여전히 RUNNING 이다 — 조회가 DB 와 다른 말을 하지 않는다.
        assertThat(state.status()).isEqualTo(LayerStatus.RUNNING);
        assertThat(state.stalled()).isTrue();

        // 회의 단위로 접으면 FAILED 다. RUNNING 으로 접으면 「AI 처리 중」이 끝나지 않고
        // ANLZ-01 이 그 상태를 보고 409 를 줘서 사람이 다시 돌릴 수도 없다.
        ProcessingStatus folded = ProcessingStatus.of(List.of(
                new LayerProgress(state.layer(), state.status(),
                        state.tokensIn(), state.tokensOut(), state.stalled())));
        assertThat(folded.status()).isEqualTo(ProcessingStatus.OverallStatus.FAILED);
    }

    @Test
    @DisplayName("V5.18 이전부터 갇혀 있던 행도 회수된다 — 심장 기록이 없으면 시작 시각을 본다")
    void 심장_기록이_없는_행도_회수된다() {
        long meetingId = 9_305L;
        lock(meetingId, LayerName.L1);

        // 이 마이그레이션 이전에 남은 행의 모양 — heartbeat_at 이 아예 없다.
        jdbcTemplate.update("UPDATE analysis_layer SET heartbeat_at = NULL, started_at = ? "
                        + "WHERE meeting_id = ? AND layer = ?",
                LocalDateTime.now(clock).minusMinutes(10), meetingId, LayerName.L1.wireValue());

        assertThat(lock(meetingId, LayerName.L1)).isEqualTo(LockResult.ACQUIRED);
    }

    @Test
    @DisplayName("잠금을 뺏긴 실행의 완료 기록은 무시된다 — 새 주인이 돌고 있는 계층을 닫으면 안 된다")
    void 뺏긴_실행은_계층을_닫지_못한다() {
        long meetingId = 9_306L;
        int oldAttempt = lockAttempt(meetingId, LayerName.L3);

        // A 가 멈춘 사이 B 가 회수한다. 이 순간 주인이 바뀐다.
        stopHeartbeat(meetingId, LayerName.L3, 10);
        int newAttempt = lockAttempt(meetingId, LayerName.L3);
        assertThat(newAttempt).isNotEqualTo(oldAttempt);

        // A 가 깨어나 자기 계층을 닫으려 한다.
        analysisLayerRepository.markDone(meetingId, LayerName.L3, oldAttempt, LayerRun.empty());

        /*
         * 닫히면 안 된다. 닫힌 계층은 다시 잠글 수 있으므로, 제3의 실행이 B 와 **동시에**
         * 같은 계층을 돌게 된다 — 잠금이 막으려던 상태가 잠금을 통해 만들어진다.
         */
        assertThat(stateOf(meetingId, LayerName.L3).status()).isEqualTo(LayerStatus.RUNNING);
    }

    @Test
    @DisplayName("잠금을 뺏긴 실행의 실패 기록도 무시된다 — 멀쩡히 도는 실행이 실패로 남으면 안 된다")
    void 뺏긴_실행은_계층을_실패시키지_못한다() {
        long meetingId = 9_307L;
        int oldAttempt = lockAttempt(meetingId, LayerName.L4);
        stopHeartbeat(meetingId, LayerName.L4, 10);
        lockAttempt(meetingId, LayerName.L4);

        analysisLayerRepository.markFailed(meetingId, LayerName.L4, oldAttempt,
                "ORCHESTRATION_ERROR", "뺏긴 실행이 뒤늦게 실패했다", LayerRun.empty());

        assertThat(stateOf(meetingId, LayerName.L4).status()).isEqualTo(LayerStatus.RUNNING);
    }

    @Test
    @DisplayName("뺏긴 실행의 심장은 새 주인을 살려두지 않는다 — 회수 장치가 회수를 막게 된다")
    void 뺏긴_실행의_심장은_무시된다() {
        long meetingId = 9_308L;
        int oldAttempt = lockAttempt(meetingId, LayerName.L2);
        stopHeartbeat(meetingId, LayerName.L2, 10);
        lockAttempt(meetingId, LayerName.L2);

        // 새 주인(B)도 죽었다. 그 심장이 멈춘 상태를 만든다.
        stopHeartbeat(meetingId, LayerName.L2, 10);
        // 그런데 옛 주인(A)이 아직 살아서 심장을 찍는다.
        analysisLayerRepository.heartbeat(meetingId, LayerName.L2, oldAttempt);

        // A 의 심장이 B 의 잠금을 살려두면 이 계층은 다시 영원히 갇힌다(#177 로 되돌아간다).
        assertThat(stateOf(meetingId, LayerName.L2).stalled()).isTrue();
        assertThat(lock(meetingId, LayerName.L2)).isEqualTo(LockResult.ACQUIRED);
    }

    @Test
    @DisplayName("현재 주인의 쓰기는 그대로 반영된다 — 조이기만 하고 막지는 않는다")
    void 현재_주인의_쓰기는_반영된다() {
        long meetingId = 9_309L;
        int attempt = lockAttempt(meetingId, LayerName.L7);

        analysisLayerRepository.markDone(meetingId, LayerName.L7, attempt, LayerRun.empty());

        assertThat(stateOf(meetingId, LayerName.L7).status()).isEqualTo(LayerStatus.DONE);
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    /* 실행 번호 행이 없으면 순서 검사는 통과한다(AnalysisLayerLockAcquirer) — 여기서 볼 축이 아니다. */
    private LockResult lock(long meetingId, LayerName layer) {
        return analysisLayerRepository.tryLock(meetingId, layer, 1L).result();
    }

    /* 잠금과 함께 받은 주인 번호(#212). */
    private int lockAttempt(long meetingId, LayerName layer) {
        return analysisLayerRepository.tryLock(meetingId, layer, 1L).attempt();
    }

    /* 잠근 프로세스가 죽어 심장이 멈춘 상태를 만든다. */
    private void stopHeartbeat(long meetingId, LayerName layer, int minutesAgo) {
        jdbcTemplate.update("UPDATE analysis_layer SET heartbeat_at = ? WHERE meeting_id = ? AND layer = ?",
                LocalDateTime.now(clock).minusMinutes(minutesAgo), meetingId, layer.wireValue());
    }

    private LayerState stateOf(long meetingId, LayerName layer) {
        return analysisLayerRepository.findStates(meetingId).stream()
                .filter(state -> state.layer() == layer)
                .findFirst()
                .orElseThrow();
    }

    private int attemptCountOf(long meetingId, LayerName layer) {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM analysis_layer WHERE meeting_id = ? AND layer = ?",
                Integer.class, meetingId, layer.wireValue());
    }
}
