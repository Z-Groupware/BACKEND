package com.module06.backend.capture.application.port.in;

/*
 * cap(캡처)의 10분/40청크 자동 트리거가 이 도메인(capture)에게 "블록 하나를 만들고 제출해달라"고
 * 요청하는 인바운드 포트다.
 *
 * <h2>왜 인바운드 포트인가 — SttBlockRepository(port.out)를 직접 부르지 않는다</h2>
 * cap이 이 도메인의 아웃바운드 포트(SttBlockRepository, SttJobPort)를 직접 호출하면 cap이
 * capture의 내부 저장/제출 구현에 묶인다. 다른 도메인 간 연동(metering의 RecordTokenUsagePort를
 * capture가 부르는 것)과 같은 모양으로, 소비자는 항상 대상 도메인의 port.in만 본다.
 *
 * <h2>cap은 SttCutReason(enum)을 모른다</h2>
 * cutReason을 문자열로 받는다 — cap이 이 도메인의 enum에 의존하지 않게 하기 위함이다
 * (CapSttBlockReferenceEntity가 status를 String으로 읽는 것과 같은 원칙). 알 수 없는 값이면
 * 이 포트 구현체가 IllegalArgumentException을 던진다.
 */
public interface CreateSttBlockPort {

    /** 블록을 생성(QUEUED)하고 STT 제공자에 즉시 제출한다. */
    void createAndSubmitBlock(CreateSttBlockCommand command);

    record CreateSttBlockCommand(
            long meetingId,
            int blockSeq,
            int startOffsetMs,
            int endOffsetMs,
            String cutReason,
            String audioS3Key
    ) {
    }
}
