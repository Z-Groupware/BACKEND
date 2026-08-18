package com.module06.backend.capture.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.in.CreateSttBlockPort;
import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.application.port.out.SttJobPort;
import com.module06.backend.capture.application.port.out.SttJobPort.SttJob;

/*
 * 10분/40청크 자동 트리거(cap 소유)가 만든 블록을 생성·제출한다.
 *
 * <h2>제출과 생성을 한 트랜잭션에 둔다(SttBlockService.retry와 동일 원칙)</h2>
 * 제출이 실패했는데 QUEUED 행만 커밋되면, 그 블록은 실패 흔적(FAILED)이 아니라 QUEUED로 영원히
 * 남아 재처리(STT-04, FAILED만 대상) 대상도 못 되는 고아 상태가 된다. 생성을 먼저 하고 그 안에서
 * 제출이 던지면 트랜잭션 전체가 롤백되어, 실패 시 아무 흔적도 안 남는 편이 낫다 — cap의 트리거는
 * best-effort라 실패하면 그냥 이번 회차 블록 생성을 건너뛴 것으로 취급한다.
 *
 * <h2>markQueuedForRetry와 달리 CAS가 필요 없다</h2>
 * 이 블록 자리를 처음 만드는 것이라 경합할 기존 행이 없다 — cap의 청크 카운터가 순차적으로만
 * 트리거를 발화시킨다(회의 하나에 동시에 두 번 40청크째를 셀 수 없다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SttBlockCreationService implements CreateSttBlockPort {

    private static final String PROVIDER = "aws-transcribe";

    private final SttBlockRepository sttBlockRepository;
    private final SttJobPort sttJobPort;
    private final SttJobNameFactory sttJobNameFactory;

    @Override
    @Transactional
    public void createAndSubmitBlock(CreateSttBlockCommand command) {
        // 최초 제출이라 재시도 횟수 0이다 — 재처리가 -r1, -r2로 이어지므로 여기서 -r0으로 시작해야
        // 계정 내 유일성이 계속 성립한다. 형식 자체는 SttJobNameFactory가 갖는다(예전엔 이 자리에
        // 규칙을 손으로 다시 적어서, 재처리 쪽 jobNameOf와 갈릴 수 있었다).
        String jobName = sttJobNameFactory.create(command.meetingId(), command.blockSeq(), 0);

        long blockId = sttBlockRepository.createQueued(
                command.meetingId(), command.blockSeq(), command.startOffsetMs(), command.endOffsetMs(),
                command.cutReason(), command.audioS3Key(), PROVIDER, jobName);

        sttJobPort.submit(new SttJob(
                command.meetingId(), command.blockSeq(), PROVIDER, jobName,
                command.audioS3Key(), command.startOffsetMs(), command.endOffsetMs()));

        // blockId는 지금 호출자가 안 쓰지만, 다음에 조회 API가 이 흐름을 감사(audit)해야 할 때
        // 로그로 남겨둔다.
        log.info("STT 블록 자동 생성·제출 — meetingId={} blockSeq={} blockId={} job={}",
                command.meetingId(), command.blockSeq(), blockId, jobName);
    }
}
