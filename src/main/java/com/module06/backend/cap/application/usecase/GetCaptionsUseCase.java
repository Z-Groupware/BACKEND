package com.module06.backend.cap.application.usecase;

import java.math.BigDecimal;
import java.util.List;

// 컨트롤러가 부르는 "명찰" — 자막 전체 조회(CAP-12)의 실제 구현체(GetCaptionsService)를 몰라도 되게 한다.
public interface GetCaptionsUseCase {

    // 열람 권한 확인 후 이 회의에 쌓인 자막 전체를 시간순으로 반환한다.
    Result getCaptions(Long meetingId, Requester requester);

    /**
     * 요청자 신원 — 열람 권한 판정용. 참석자거나, 같은 회사의 owner/admin(감독 열람)이면 허용한다.
     * 재생 URL(CAP-14)의 Requester와 동일한 판정 기준 — GetPlaybackUrlUseCase.Requester 참고.
     */
    record Requester(Long memberId, Long companyId, String role, boolean isAdmin) {
        public boolean isOwnerOrAdmin() {
            return "OWNER".equals(role) || isAdmin;
        }
    }

    record Result(List<CaptionItem> captions) {
    }

    /** 자막 조각 하나. personId는 발신자(memberId)를 스펙 응답 필드명 그대로 노출한 것이다. */
    record CaptionItem(int seq, Long personId, int startMs, int endMs, String text, BigDecimal rms) {
    }
}
