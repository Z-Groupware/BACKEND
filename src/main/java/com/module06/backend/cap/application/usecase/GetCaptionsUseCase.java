package com.module06.backend.cap.application.usecase;

import java.math.BigDecimal;
import java.util.List;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;

// 컨트롤러가 부르는 "명찰" — 자막 전체 조회(CAP-12)의 실제 구현체(GetCaptionsService)를 몰라도 되게 한다.
public interface GetCaptionsUseCase {

    // 열람 권한 확인(참석자 / 같은 회사 owner·admin / 프로젝트 멤버 — CapMeetingAccessGuard가 판정) 후
    // 이 회의에 쌓인 자막 전체를 시간순으로 반환한다.
    //
    // 요청자 신원은 GetPlaybackUrlUseCase와 동일하게 CapMeetingAccessGuard.ViewerContext를 그대로 쓴다
    // (예전엔 이 인터페이스마다 필드가 동일한 Requester 레코드를 따로 뒀었는데, 결국 가드에 넘기기 전
    // 그대로 복사하는 매핑 메서드만 두 벌 생겼던 중복이라 통합했다).
    Result getCaptions(Long meetingId, CapMeetingAccessGuard.ViewerContext requester);

    record Result(List<CaptionItem> captions) {
    }

    /** 자막 조각 하나. personId는 발신자(memberId)를 스펙 응답 필드명 그대로 노출한 것이다. */
    record CaptionItem(int seq, Long personId, int startMs, int endMs, String text, BigDecimal rms) {
    }
}
