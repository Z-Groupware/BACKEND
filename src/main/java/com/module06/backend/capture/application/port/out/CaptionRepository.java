package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.CaptionChunk;

/*
 * caption_chunk 읽기 포트다(V5.2).
 *
 * ⚠ **이 테이블의 주인은 김현지(CAP)다.** CAP-11 이 쓰고 CAP-12·13 이 조회한다. 여기서는
 * L1 화자 귀속의 판정 근거로 **읽기만** 한다 — 명세도 "L1 화자 귀속이 caption_chunk.rms 를
 * 읽는다"로 그 방향만 적어 뒀다.
 *
 * 빈 목록이 들어오면 L1 은 전원 판정 포기로 끝난다(정상 동작). 참석자가 브라우저 자막을
 * 보내지 않은 회의가 그렇다 — 화자가 NULL 로 남는 것이 임의로 채우는 것보다 낫다.
 *
 * ⚠ 예전 주석이 "CAP-11 이 아직 구현되지 않아 이 포트는 항상 빈 목록을 돌려준다"고 적어
 * 두었다. **지금은 아니다** — CAP-11(SubmitCaptionsService)은 구현돼 있고 자막이 실제로
 * 쌓인다. 그 문장을 믿고 L1 을 죽은 코드로 취급하면 안 된다.
 */
public interface CaptionRepository {

    /*
     * 회의의 자막 전체를 읽는다.
     *
     * 발화마다 시간창 질의를 던지지 않고 한 번에 읽는 이유 — 2시간 회의면 발화가 수천 건이라
     * 발화당 질의는 수천 번의 왕복이 된다. 자막은 회의 하나 분량이 메모리에 들어가는 크기이고
     * (VARCHAR(1000) × 청크 수), 시간창 매칭은 어차피 전부 훑어야 하는 계산이다.
     */
    List<CaptionChunk> findByMeeting(long meetingId);
}
