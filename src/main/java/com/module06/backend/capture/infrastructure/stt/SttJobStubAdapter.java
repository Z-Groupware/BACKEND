package com.module06.backend.capture.infrastructure.stt;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.SttJobPort;

/*
 * STT 제출의 스텁이다. AWS Transcribe 호출은 후속이다.
 *
 * cap 의 {@code MeetingRecordingSttStubAdapter} 와 같은 "실 어댑터 전 스텁" 패턴이고, 그쪽
 * 주석이 예고한 「실제 stt_block 생성·Transcribe 호출은 이태연 STT 도메인」의 절반이 여기다.
 *
 * <h2>성공한 척한다 — 그래서 로그가 유일한 흔적이다</h2>
 * 예외를 던지면 재처리 API 가 통째로 막혀 상태 전이·권한·잡 이름 규칙을 실제로 확인할 수 없다.
 * 대신 **제출 내용을 통째로 남긴다** — 실 어댑터가 붙었을 때 같은 값이 나가는지 이 로그와
 * 대조할 수 있어야 하고, 그때까지 "제출은 됐는데 결과가 안 온다"의 원인이 여기임을 볼 수 있어야
 * 한다.
 *
 * ⚠ 이 스텁이 도는 동안 블록은 QUEUED 에서 더 나아가지 않는다. RUNNING·DONE 으로 옮기는 것은
 * 제공자 콜백(후속)의 몫이고, 그게 없으므로 **재처리한 블록은 QUEUED 로 머문다.** 상태가 안
 * 바뀌는 것이 버그로 보이지 않게 여기 적어 둔다.
 */
@Slf4j
@Component
public class SttJobStubAdapter implements SttJobPort {

    @Override
    public void submit(SttJob job) {
        log.info("STT 제출(stub) — meetingId={} blockSeq={} provider={} job={} s3Key={} 구간={}~{}ms. "
                        + "실제 Transcribe 호출·상태 전이(RUNNING·DONE)는 후속 STT 인프라에서 수행.",
                job.meetingId(), job.blockSeq(), job.provider(), job.providerJobName(),
                job.audioS3Key(), job.startOffsetMs(), job.endOffsetMs());
    }
}
