package com.module06.backend.cap.application.usecase;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;

// 컨트롤러가 부르는 "명찰" — 재생용 presigned URL 발급(CAP-14)의 실제 구현체(PlaybackUrlService)를 몰라도 되게 한다.
public interface GetPlaybackUrlUseCase {

    // 열람 권한 확인(참석자 / 같은 회사 owner·admin / 프로젝트 멤버 — CapMeetingAccessGuard가 판정) 후
    // 녹음본 재생용 presigned GET URL을 발급한다.
    //
    // 요청자 신원은 GetCaptionsUseCase와 동일하게 CapMeetingAccessGuard.ViewerContext를 그대로 쓴다
    // (role/isAdmin/teamId는 identity·project 도메인 소유값이라 cap은 enum 의존 없이 토큰 클레임 그대로
    // 받아 문자열/원시값으로 판정한다 — 예전엔 이 인터페이스마다 필드가 동일한 Requester 레코드를 따로
    // 뒀었는데, 가드에 넘기기 전 그대로 복사하는 매핑 메서드만 두 벌 생겼던 중복이라 통합했다).
    Result getPlaybackUrl(Long meetingId, CapMeetingAccessGuard.ViewerContext requester);

    /**
     * 발급 결과.
     *
     * @param url        재생용 S3 presigned GET URL(HTTP Range 지원).
     * @param expiresIn  URL 만료 시간(초). 3시간(10800) — 단일 파일+네이티브 탐색바 구조라 넉넉히.
     * @param durationMs 녹음 전체 길이(ms). 파이프라인이 아직 duration을 안 채웠으면 0.
     */
    record Result(
            String url,
            int expiresIn,
            long durationMs
    ) {
    }
}
