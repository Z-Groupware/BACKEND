package com.module06.backend.capture.application.port.out;

/*
 * stt_gap(V5.5) 접근 포트다. 지금은 **분배 확정의 관문**으로만 쓴다.
 *
 * 구멍은 "받아쓰기가 죽어서 아무도 못 들은 구간"이다. 그 구간에서 나온 할 일은 어디에도 없고,
 * 구멍을 모른 채 분배를 확정하면 그 액션은 영구히 사라진다 — 사람은 회의가 온전히 처리된 줄
 * 안다(V5.5 주석).
 *
 * ⚠ **채우는 쪽이 아직 없다**(조립·Transcribe). 그래서 지금은 항상 0 이고 관문이 열려 있는
 * 것처럼 보인다. 그 경로가 붙는 순간 이 검사가 저절로 동작하도록 지금 넣어둔다 — 나중에
 * 붙이려면 "왜 분배가 막히지"를 아무도 모르는 상태에서 디버깅하게 된다.
 */
public interface SttGapRepository {

    /*
     * @return 사람이 아직 확인하지 않은(resolved_at IS NULL) 구멍 수
     */
    int countUnresolved(long meetingId);
}
