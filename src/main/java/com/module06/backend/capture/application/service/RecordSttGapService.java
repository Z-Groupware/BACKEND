package com.module06.backend.capture.application.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.in.RecordSttGapPort;
import com.module06.backend.capture.application.port.out.SttGapRepository;

/*
 * cap 이 발견한 녹음 구멍을 기록한다.
 *
 * <h2>얇다. 그게 맞다</h2>
 * 판정은 cap 이 한다(어느 seq 가 빠졌는지는 recording_part 를 아는 쪽만 안다). 여기가 하는 일은
 * 그 사실을 stt_gap 에 남기는 것 하나이고, 그래야 분배 확정 관문(RVW-05)과 CAP-06 이 받아쓰기
 * 구멍과 **같은 자리에서** 그 구멍을 본다 — 표를 둘로 나누면 관문도 두 개가 된다.
 *
 * <h2>회사 관문을 지나지 않는다</h2>
 * 사람의 요청이 아니라 도메인 간 호출이다. cap 은 이미 자기 경로에서 회의 접근을 확인했고
 * (CapMeetingAccessGuard), 여기서 다시 막으면 같은 검증이 두 곳에 생겨 규칙이 갈릴 자리가 된다 —
 * CreateSttBlockPort 가 같은 이유로 관문을 두지 않았다.
 *
 * <h2>구간을 검증한다</h2>
 * 시작이 끝보다 크거나 같은 구멍은 기록하지 않는다. 그런 값이 들어오면 화면의 배너가 "0초짜리
 * 구멍"을 가리키고, 사람이 다시 들을 자리를 못 찾는다 — 구멍 레코드의 목적이 "어디를 다시
 * 들어야 하나"라서 구간이 없으면 그 레코드는 관문만 막고 아무것도 알려주지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordSttGapService implements RecordSttGapPort {

    private final SttGapRepository sttGapRepository;

    @Override
    public void recordRecordingGap(long meetingId, int startOffsetMs, int endOffsetMs,
                                   RecordingGapReason reason) {
        if (startOffsetMs < 0 || endOffsetMs <= startOffsetMs) {
            /*
             * 예외를 올리지 않는다. 부르는 쪽은 회의 중에 도는 best-effort 경로이고(자동 블록
             * 트리거), 여기서 터뜨리면 구멍 하나 때문에 그 회차의 블록 생성이 통째로 건너뛰어진다.
             * 잘못된 값이 왔다는 사실은 로그로 남는다.
             */
            log.error("구멍 구간이 올바르지 않아 기록하지 않는다 — meetingId={} 구간={}~{}ms 사유={}",
                    meetingId, startOffsetMs, endOffsetMs, reason);
            return;
        }
        sttGapRepository.replaceRecordingGap(meetingId, startOffsetMs, endOffsetMs, reason.name());
    }
}
