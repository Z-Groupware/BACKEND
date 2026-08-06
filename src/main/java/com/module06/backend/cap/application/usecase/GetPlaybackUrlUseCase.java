package com.module06.backend.cap.application.usecase;

// 컨트롤러가 부르는 "명찰" — 재생용 presigned URL 발급(CAP-14)의 실제 구현체(PlaybackUrlService)를 몰라도 되게 한다.
public interface GetPlaybackUrlUseCase {

    // 열람 권한 확인 후 녹음본 재생용 presigned GET URL을 발급한다.
    Result getPlaybackUrl(Long meetingId, Requester requester);

    /**
     * 요청자 신원 — 열람 권한 판정용. 참석자거나, 같은 회사의 owner/admin(감독 열람)이면 허용한다.
     * (role/isAdmin은 identity 도메인 소유값이라 cap은 enum 의존 없이 토큰 클레임 그대로 받아 문자열로 판정)
     */
    record Requester(Long memberId, Long companyId, String role, boolean isAdmin) {
        public boolean isOwnerOrAdmin() {
            return "OWNER".equals(role) || isAdmin;
        }
    }

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
