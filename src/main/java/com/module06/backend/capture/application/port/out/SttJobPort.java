package com.module06.backend.capture.application.port.out;

/*
 * STT 제공자에 블록 하나를 제출한다(AWS Transcribe · whisper).
 *
 * <h2>왜 포트로 두나 — 제공자가 하나가 아니다</h2>
 * STT-04 가 요청 본문으로 provider 를 받는다(`{"provider":"whisper"}`). 실패한 블록을 **다른
 * 제공자로** 다시 돌리는 것이 그 API 의 목적 중 하나라, 호출부가 특정 SDK 에 묶이면 그 선택이
 * 성립하지 않는다.
 *
 * <h2>실 어댑터가 아직 없다</h2>
 * AWS Transcribe 호출은 후속이다(cap 의 {@code MeetingRecordingSttStubAdapter} 가 예고해 둔
 * 자리와 같다). 지금은 스텁이 로그만 남긴다 — **200 에 빈 결과를 주는 대신 제출 자체를
 * 기록으로 남기는 쪽**이라, 미구현이 "제출했는데 결과가 안 온다"로 위장되지 않는다.
 */
public interface SttJobPort {

    /*
     * 블록을 제출한다. 실패하면 예외를 올린다 — **삼키면 안 된다.**
     *
     * 사람이 재처리 버튼을 누르고 202 를 받은 자리다. 여기서 조용히 실패하면 화면은
     * "재처리를 시작했습니다"라고 말하는데 아무 일도 일어나지 않고, 그 블록은 QUEUED 인 채로
     * 영원히 남는다 — 사람이 다시 누를 수도 없다(QUEUED 는 재처리 대상이 아니다).
     *
     * <h2>실제로 쓰인 잡 이름을 돌려준다 — 요청한 이름과 다를 수 있다</h2>
     * 호출자가 지어 준 이름이 <b>제공자 쪽에서 이미 다른 오디오에 쓰이고 있으면</b> 그 이름으로는
     * 영영 제출할 수 없다(2026-08-18 meeting-2·3). 구현은 그때 이름을 바꿔 제출할 수 있고,
     * 그러면 <b>호출자가 stt_block.provider_job_name 을 그 값으로 고쳐야 한다</b> — 폴링은 저장된
     * 이름으로 결과를 조회하므로(SttJobResultPort#fetch), 안 고치면 우리가 만든 잡을 우리가 못
     * 찾는다.
     *
     * 돌아온 값이 요청한 이름과 같으면 고칠 것이 없다. 호출자가 매번 비교하게 두는 이유는,
     * "바뀌었으면 알려 준다"는 계약이 <b>호출자 쪽 코드에 눈에 보이게</b> 남아야 나중에 제출
     * 경로가 하나 더 생겼을 때 그 자리에서 빠뜨리지 않기 때문이다.
     *
     * @param providerJobName 계정 내 유일해야 하는 잡 이름. retryCount 가 들어 있다
     * @return 제공자에 실제로 접수된 잡 이름
     */
    String submit(SttJob job);

    record SttJob(
            long meetingId,
            int blockSeq,
            String provider,
            String providerJobName,
            String audioS3Key,
            int startOffsetMs,
            int endOffsetMs
    ) {
    }
}
