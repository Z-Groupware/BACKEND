package com.module06.backend.capture.infrastructure.stt;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.SttJobResultPort;

/*
 * 로컬·테스트용 결과 조회 스텁이다. 운영은 {@link SttTranscribeResultAdapter} 가 대신한다.
 *
 * <h2>QUEUED 를 돌려준다 — 완료를 흉내내지 않는다</h2>
 * 가짜 전사를 만들어 COMPLETED 로 답할 수도 있지만 그러면 **로컬에서 지어낸 문장이 정본으로
 * 적재되고**, 그 위에서 계층이 돌아 그럴듯한 요약이 나온다. 그 요약을 보고 파이프라인이
 * 동작한다고 판단하게 되는 것이 이 스텁이 만들 수 있는 최악이다.
 *
 * QUEUED 를 돌려주면 블록은 QUEUED 에 머문다 — 실 어댑터가 붙기 전과 같은 상태이고,
 * 분석 시작 관문이 그 회의를 정직하게 막는다.
 *
 * 그래서 로컬에서는 워커가 매 주기 이 로그만 남긴다. 시끄러우면
 * {@code capture.stt-polling.enabled=false} 로 끈다(SttResultPollingScheduler).
 */
@Slf4j
@Component
@Profile("!prod")
public class SttJobResultStubAdapter implements SttJobResultPort {

    @Override
    public SttJobOutcome fetch(String providerJobName) {
        log.debug("STT 결과 조회(stub) — job={}. 실 어댑터 전까지 QUEUED 로 답한다.", providerJobName);
        return SttJobOutcome.of(State.QUEUED);
    }
}
