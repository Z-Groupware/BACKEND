package com.module06.backend.capture.application.usecase;

import java.util.List;

import com.module06.backend.capture.application.port.out.TranscriptRepository.UtteranceView;

/* ANLZ-05 · 정본 스크립트 조회. */
public interface GetTranscriptsUseCase {

    /*
     * @param cursor 직전 페이지의 마지막 자리(불투명 문자열). null 이면 첫 페이지
     * @param ids    특정 발화만 볼 때. 지정하면 커서를 무시하고 그것만 돌려준다
     */
    TranscriptPage getTranscripts(long companyId, long meetingId, String cursor, List<Long> ids);

    /*
     * nextCursor 가 null 이면 마지막 페이지다.
     *
     * 총 건수를 함께 주지 않는다. 세려면 COUNT 를 한 번 더 돌려야 하는데, 정본은 회의당 수천
     * 건이고 화면은 "몇 개 중 몇 번째"를 쓰지 않는다 — 스크롤로 이어 보는 목록이다.
     */
    record TranscriptPage(List<UtteranceView> utterances, String nextCursor) {
    }
}
