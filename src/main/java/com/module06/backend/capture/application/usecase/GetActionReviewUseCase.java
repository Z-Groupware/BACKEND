package com.module06.backend.capture.application.usecase;

import com.module06.backend.capture.application.result.ActionReview;

/*
 * RVW-01 · 액션 분배 검토 조회.
 *
 * 파이프라인 산출물을 **사람이 처음 보는 자리**다. L7 이 가른 두 묶음(「AI 확신도 높음」 ·
 * 「AI 확인 필요」)이 여기서 화면으로 나간다.
 *
 * @param reviewStatus 필터. null 이면 전체다(명세: 생략 시 전체)
 */
public interface GetActionReviewUseCase {

    ActionReview getReview(long companyId, long meetingId, String reviewStatus);
}
