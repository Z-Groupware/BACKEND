package com.module06.backend.capture.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;

/*
 * analysis_layer(V5.6) 매핑이다.
 *
 * layer 를 String 으로 들고 있는 이유 — 컬럼이 VARCHAR(8) 이고 값이 "L1.5"·"L3.5" 라
 * enum 이름과 다르다. @Enumerated 로 매핑하면 "L1_5" 가 저장돼 Python 쪽 계약과 갈린다.
 * 변환은 {@link LayerName#wireValue()} 한 곳에서만 한다.
 *
 * 스키마 주인은 Flyway 다. 이 엔티티는 기존 테이블을 검증하고 읽고 쓰는 역할만 한다.
 */
@Entity
@Table(name = "analysis_layer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisLayerJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* "L1.5"·"L3.5" 같은 전송 값이 그대로 들어간다. LayerName.name() 이 아니다. */
    @Column(name = "layer", nullable = false, length = 8)
    private String layer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LayerStatus status;

    /* 계층 단위 재시도 횟수다. 워커 단위 백오프와는 다른 축이라 여기 둔다(V5.6 주석). */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "tokens_in", nullable = false)
    private int tokensIn;

    @Column(name = "tokens_out", nullable = false)
    private int tokensOut;

    @Column(name = "model_name", length = 60)
    private String modelName;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static AnalysisLayerJpaEntity running(long meetingId, LayerName layer, LocalDateTime now) {
        AnalysisLayerJpaEntity entity = new AnalysisLayerJpaEntity();
        entity.meetingId = meetingId;
        entity.layer = layer.wireValue();
        entity.status = LayerStatus.RUNNING;
        entity.attemptCount = 1;
        entity.startedAt = now;
        return entity;
    }

    /*
     * 재실행 — RUNNING 으로 되돌리고 시도 횟수를 올린다.
     *
     * 이전 실행의 메타데이터를 **전부 지운다.** 남겨 두면 이번 시도가 모델을 부르기도 전에
     * 실패했을 때 지난 실행의 모델·프롬프트 버전이 이번 것으로 읽힌다.
     *
     * ⚠ tokensIn/Out 은 지우지 않는다. 재시도로 태운 토큰도 실제로 나간 비용이라 0 으로
     *   되돌리면 QLTY-03 이 실패한 시도의 비용을 잃는다 — 그래서 아래 markDone·markFailed 는
     *   덮어쓰지 않고 누적한다.
     *
     * 이 메서드는 쓰기 잠금을 걸고 읽은 엔티티에만 호출된다
     * (SpringDataAnalysisLayerRepository#findWithLockByMeetingIdAndLayer).
     */
    public void restart(LocalDateTime now) {
        this.status = LayerStatus.RUNNING;
        this.attemptCount += 1;
        this.startedAt = now;
        this.finishedAt = null;
        this.errorCode = null;
        this.errorMessage = null;
        this.modelName = null;
        this.promptVersion = null;
    }

    /*
     * 토큰은 시도마다 **누적한다.** 실패한 시도에 쓴 토큰도 실제로 나간 비용이라,
     * 덮어쓰면 QLTY-03 이 그만큼 싸게 본다.
     */
    public void markDone(LayerRun run, LocalDateTime now) {
        this.status = LayerStatus.DONE;
        this.tokensIn += run.tokensIn();
        this.tokensOut += run.tokensOut();
        this.modelName = run.modelName();
        this.promptVersion = run.promptVersion();
        this.finishedAt = now;
    }

    /*
     * @param spent 실패 전까지 실제로 쓴 토큰. L3 처럼 주제마다 부르는 계층은 3번째에서
     *              터져도 앞의 2번은 과금됐다 — 그걸 버리면 비용이 조용히 과소 집계된다.
     */
    public void markFailed(String errorCode, String errorMessage, LayerRun spent, LocalDateTime now) {
        this.status = LayerStatus.FAILED;
        this.errorCode = errorCode;
        // 컬럼이 VARCHAR(500) 이다. 넘치는 메시지로 저장이 터지면 실패 기록 자체가 사라진다 —
        // 그러면 "왜 실패했는지 모르는 실패"가 되어 조사할 출발점이 없어진다.
        this.errorMessage = clip(errorMessage);
        if (spent != null) {
            this.tokensIn += spent.tokensIn();
            this.tokensOut += spent.tokensOut();
            // 모델·프롬프트는 실제로 부른 적이 있을 때만 남는다.
            if (spent.modelName() != null) {
                this.modelName = spent.modelName();
                this.promptVersion = spent.promptVersion();
            }
        }
        this.finishedAt = now;
    }

    public LayerName layerName() {
        return LayerName.fromWireValue(layer);
    }

    private static String clip(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
