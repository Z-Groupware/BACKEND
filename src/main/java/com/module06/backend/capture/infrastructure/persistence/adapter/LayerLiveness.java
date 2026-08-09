package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Duration;
import java.time.LocalDateTime;

import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.infrastructure.persistence.entity.AnalysisLayerJpaEntity;

/*
 * RUNNING 계층이 **아직 살아 있는가**를 가른다(#177).
 *
 * 잠금을 회수하는 쪽(AnalysisLayerLockAcquirer)과 화면에 보여주는 쪽
 * (AnalysisLayerPersistenceAdapter#findStates)이 같은 기준을 써야 한다. 갈리면 잠금은
 * 풀렸는데 화면은 「AI 처리 중」이거나, 반대로 화면은 멈췄다는데 재실행이 막힌다.
 *
 * <h2>유예를 왜 이 값으로 잡나</h2>
 * heartbeat 는 계층을 잡을 때와 **모델 호출이 돌아올 때마다** 찍힌다. 호출 하나의 상한이
 * ai.read-timeout(5초)이므로 살아 있는 실행의 갱신 간격은 초 단위다. 5분이면 그 간격의
 * 수십 배라 살아 있는 실행을 죽었다고 판정할 여지가 사실상 없다.
 *
 * 오탐의 대가가 크기 때문에 이렇게 넉넉히 잡는다 — 살아 있는 실행을 회수하면 같은 회의를
 * 두 번 태우고(토큰 두 배) 두 실행의 결과가 서로 덮는다(#134 가 막으려던 상태). 반대로
 * 늦게 회수하는 대가는 사람이 5분 더 기다리는 것뿐이다. **한쪽으로만 틀리게 만든다.**
 *
 * 설정값이 아니라 상수인 이유 — 테스트 설정(application.yaml)이 main 을 통째로 가리는
 * 구조라(그 파일 머리말) 키를 늘리면 양쪽에 넣어야 하고, 한쪽이 빠지면 컨텍스트를 띄우는
 * 테스트가 전부 죽는다. 튜닝이 필요해지면 그때 프로퍼티로 올린다.
 */
final class LayerLiveness {

    static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private LayerLiveness() {
    }

    /*
     * 이 계층이 RUNNING 인데 심장이 멈췄는가.
     *
     * RUNNING 이 아니면 언제나 false 다 — DONE·FAILED 는 이미 끝난 것이고, 거기에 "멈췄다"를
     * 붙이면 화면이 끝난 계층을 사고로 보여준다.
     */
    static boolean isStalled(AnalysisLayerJpaEntity entity, LocalDateTime now) {
        if (entity.getStatus() != LayerStatus.RUNNING) {
            return false;
        }
        LocalDateTime lastAlive = entity.lastAliveAt();
        if (lastAlive == null) {
            // 시작 시각조차 없는 RUNNING 이다. 기준이 없으므로 회수 대상으로 본다 —
            // 그대로 두면 그 행은 어떤 경로로도 풀리지 않는다.
            return true;
        }
        return lastAlive.isBefore(now.minus(STALE_AFTER));
    }
}
