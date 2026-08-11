package com.module06.backend.project.application.usecase;

/* comment.
    2026-08-11 — 오너 대시보드 KPI 카드 중 project(C) 소유분(전체 프로젝트·마감 D-7)만 담당한다.
    "전체 사원"·"휴직자" 카드는 identity/leave(B) 소유 데이터라 이 유스케이스의 스코프 밖이다
    (0절 1항, 이슈 #352 — 각 도메인이 자기 소유 숫자만 낸다).

    연결된 클래스
    - ProjectService     : 구현체 (application.service)
    - ProjectController  : 호출자 (presentation)
*/
public interface GetOwnerDashboardSummaryUseCase {

    OwnerDashboardSummary getOwnerDashboardSummary(Long companyId);

    // dueSoonProjectCount는 "마감 D-7" — 오늘부터 7일 이내 마감이고 완료(DONE)가 아닌 프로젝트.
    record OwnerDashboardSummary(long totalProjectCount, long dueSoonProjectCount) {
    }
}
