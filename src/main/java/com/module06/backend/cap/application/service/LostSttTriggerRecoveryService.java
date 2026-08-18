package com.module06.backend.cap.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import com.module06.backend.cap.application.port.out.MeetingRecordingSttPort;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.LostSttTriggerRepository;

/*
 * 유실된 STT 트리거를 주워 다시 건다(#574).
 *
 * <h2>무엇이 유실되나</h2>
 * 비대면 업로드는 녹음 등록이 **커밋된 뒤** afterCommit 콜백에서 STT 를 건다
 * (OnlineMeetingRecordingAdapter). 그 콜백은 메모리에만 존재하는 약속이라, 커밋과 콜백 사이에
 * 프로세스가 죽거나 요청이 끊기면 그냥 사라진다 — 재시도도 기록도 로그도 없다. 남는 것은
 * "트리거하기로 했다"(stt_triggered=1)뿐이고 "실제로 걸었다"는 사실은 어디에도 없다.
 *
 * 운영 meetingId=14 가 그렇게 새어나갔다(2026-08-15). 예외 로그조차 없어서, 같은 시기
 * outputKey 400 으로 실패한 8건과 달리 원인을 찾는 데 로그 아카이브가 필요했다.
 *
 * <h2>왜 아웃박스가 아닌가</h2>
 * 아웃박스는 테이블·릴레이·정리 배치를 새로 만든다. 여기서 필요한 것은 그보다 작다 —
 * "걸기로 했는데 안 걸린 것"의 정의가 이미 DB 에 있기 때문이다(stt_triggered=1 인데
 * stt_block 0건). 그 상태를 주기적으로 확인해 다시 거는 것으로 충분하고, 스키마를 건드리지
 * 않으므로 되돌리기도 쉽다.
 *
 * <h2>여기서 던지지 않는다</h2>
 * 한 건의 재트리거가 실패해도 나머지를 계속 돈다. 밀린 물량의 첫 건이 영구 실패일 때
 * 그 한 건이 뒤의 정상 건을 전부 막으면, 이 배치가 고치려던 것과 같은 모양의 사고가 된다.
 *
 * <h2>⚠ 재트리거가 확정적으로 실패하던 상태가 있었다 (2026-08-18 meeting-2)</h2>
 * 유실에는 두 갈래가 있다. 콜백이 통째로 사라진 것(위)과, <b>제출이 AWS 에 도달했는데 응답을
 * 못 받아 우리 행만 롤백된 것</b>이다. 둘 다 여기 후보로 잡히는 모양이 같지만(stt_triggered=1
 * 인데 stt_block 0건), 뒤쪽은 AWS 에 그 이름의 잡이 <b>살아 있다.</b> 잡 이름이 결정적이라
 * 재트리거는 매번 같은 이름에 부딪히고, 예전에는 그 충돌이 곧 실패였다 — 이 배치가 24시간
 * 상한까지 실패만 반복하고 조용히 포기하는 <b>확정 실패 루프</b>였다.
 *
 * 지금은 SttTranscribeJobAdapter 가 충돌 시 그 잡이 같은 오디오를 가리키는지 확인하고 같으면
 * 채택한다. 그래서 이 배치는 <b>코드를 바꾸지 않아도</b> 그 상태를 복구한다 — 재트리거가
 * 블록을 QUEUED 로 만들고, 폴링이 이미 끝나 있는 전사를 그대로 가져간다. 판정을 여기 두지
 * 않은 이유는 제공자 사정이기 때문이다(whisper 어댑터가 붙으면 충돌의 의미부터 달라진다).
 */
@Slf4j
@Service
public class LostSttTriggerRecoveryService {

    /*
     * 유예. 이보다 새 녹음은 건드리지 않는다.
     *
     * 트리거 직후에도 stt_block 은 잠깐 0건이다 — 제출이 외부 호출이라 그 왕복만큼 비어 있고,
     * ManualRecordingService 는 그 구간을 "완료가 아니라 진행 중"으로 읽으라고 못 박아 뒀다.
     * 유예 없이 주우면 방금 정상 등록된 녹음을 다시 제출하게 되고, 잡 이름이
     * meeting-{id}-block-0-r0 로 고정이라 UNIQUE(provider_job_name) 위반으로 끝난다.
     */
    static final Duration GRACE = Duration.ofMinutes(10);

    /*
     * 재시도 상한. 이보다 오래된 녹음은 포기한다.
     *
     * 재시도 횟수를 담을 컬럼이 없어서 **나이가 그 자리를 대신한다.** 상한이 없으면 영구
     * 실패(예: 2026-08-15 에 고쳐진 outputKey 400)를 매 주기 무한히 다시 부르고, 그 반복은
     * 아무 화면에도 안 뜬다 — 조용한 실패를 조용한 재시도로 바꾸는 것뿐이다.
     *
     * 하루로 잡은 이유: 배포·재시작으로 유실된 트리거는 몇 분 안에 주워진다. 하루가 지나도
     * 안 걸렸다면 그건 유실이 아니라 **제출 자체가 계속 거부되는 상태**이고, 사람이 볼 일이다.
     */
    static final Duration RETRY_CEILING = Duration.ofHours(24);

    /*
     * 한 주기 상한. 제출은 외부 호출이라 밀린 물량을 한 번에 몰아 태우면 그 사이 들어온
     * 정상 업로드가 제공자 한도에 걸린다. 후보마다 stt_block 조회가 한 번씩 나가기도 한다
     * (LostSttTriggerRepositoryAdapter 주석).
     */
    static final int MAX_PER_CYCLE = 20;

    private final LostSttTriggerRepository lostSttTriggerRepository;
    private final MeetingRecordingSttPort meetingRecordingSttPort;
    private final Clock clock;

    public LostSttTriggerRecoveryService(LostSttTriggerRepository lostSttTriggerRepository,
                                         MeetingRecordingSttPort meetingRecordingSttPort,
                                         @Qualifier("meetingClock") Clock clock) {
        this.lostSttTriggerRepository = lostSttTriggerRepository;
        this.meetingRecordingSttPort = meetingRecordingSttPort;
        this.clock = clock;
    }

    /* @return 이번 주기에 다시 건 건수. 후보가 없으면 0 */
    public int recoverOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Recording> lost = lostSttTriggerRepository.findSttTriggeredWithoutBlocks(
                now.minus(RETRY_CEILING), now.minus(GRACE), MAX_PER_CYCLE);

        if (lost.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        for (Recording recording : lost) {
            if (retrigger(recording)) {
                recovered++;
            }
        }
        return recovered;
    }

    /*
     * 한 건을 다시 건다. 실패해도 다음 건으로 넘어간다.
     *
     * 성공을 INFO 로 남기는 이유 — 이 배치가 도는 것 자체가 **어딘가에서 트리거가 유실됐다는
     * 신호**다. 조용히 고치면 유실이 얼마나 자주 일어나는지 아무도 모르고, 그러면 근본 원인
     * (afterCommit 의존)을 고칠 판단 근거도 안 생긴다.
     */
    private boolean retrigger(Recording recording) {
        try {
            meetingRecordingSttPort.triggerWholeFileStt(recording.getMeetingId(), recording.getFileUrl());
            log.info("유실된 STT 트리거를 복구했다 — meetingId={} 등록시각={}",
                    recording.getMeetingId(), recording.getCreatedAt());
            return true;
        } catch (RuntimeException e) {
            /*
             * 여기서 멈추지 않는다. 이 건은 다음 주기에 다시 온다 — 상한(24시간)까지는.
             * 상한을 넘기면 더 안 오므로, 그 전에 사람이 이 로그를 봐야 한다.
             */
            log.error("유실된 STT 트리거 복구 실패 — meetingId={} 등록시각={}",
                    recording.getMeetingId(), recording.getCreatedAt(), e);
            return false;
        }
    }
}
