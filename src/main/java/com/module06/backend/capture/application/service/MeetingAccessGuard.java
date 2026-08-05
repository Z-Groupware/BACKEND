package com.module06.backend.capture.application.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.MeetingAccessPort;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * 캡처 파이프라인의 모든 유스케이스가 지나는 회사 스코프 관문이다.
 *
 * <h2>왜 한 곳에 두는가</h2>
 * 공개 API 18개가 전부 {@code /api/meetings/{meetingId}/...} 이고 companyId 는 토큰에서만 온다.
 * 각 유스케이스가 각자 조회 조건에 회사를 끼워 넣는 방식으로 두면 **언젠가 한 곳이 빠지고,
 * 그 API 만 조용히 뚫린다.** 실제로 그렇게 뚫려 있었다 — CAP-06 이 companyId 를 받아놓고
 * 쓰지 않아 다른 회사 회의의 처리 상태를 조회할 수 있었다(CodeRabbit PR #84 · 이슈 #100).
 *
 * <h2>인증과 다른 층이다</h2>
 * PR #108 이후 SecurityConfig 는 기본 잠금이고 {@code @PreAuthorize} 는 역할을 본다.
 * 둘 다 "로그인했나 / 그 역할인가"까지만 본다 — **"이 회의가 그 사람 회사 것인가"는 아무도
 * 보지 않는다.** 그 자리가 여기다. 로그인한 사원이 남의 회사 회의 id 를 넣는 것을 막는다.
 *
 * <h2>404 로 응답한다</h2>
 * 403 이면 "이 회의는 존재하지만 당신 것이 아니다"가 새어 나간다. id 를 훑어 남의 회사 회의
 * 개수를 셀 수 있다. 존재하지 않는 회의와 접근 불가를 같은 응답으로 덮는다.
 */
@Service
@RequiredArgsConstructor
public class MeetingAccessGuard {

    private final MeetingAccessPort meetingAccessPort;

    /*
     * 접근 가능하지 않으면 던진다. 통과하면 아무 값도 돌려주지 않는다 —
     * 반환값을 두면 호출부가 그걸 무시할 수 있고, 무시된 검증은 없는 검증이다.
     */
    public void requireAccessible(long companyId, long meetingId) {
        if (!meetingAccessPort.existsInCompany(companyId, meetingId)) {
            throw new BusinessException(CaptureErrorCode.MEETING_NOT_ACCESSIBLE);
        }
    }
}
