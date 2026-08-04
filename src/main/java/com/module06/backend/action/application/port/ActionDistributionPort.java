package com.module06.backend.action.application.port;

/* comment.
    action(C)이 선언하고, meeting(A, 김현지) 도메인이 호출하는 인바운드 포트.
    A가 회의 요약을 마친 뒤 이 포트를 통해 팀 액션·개인 액션을 일괄 생성한다(FR-AC-01 정상 경로).
    요청 스키마는 아직 A와 확정 전이라 로컬 stub으로 개발을 우선 진행한다(A 병목 회피).

    연결된 클래스
    - Action                : 이 포트를 통해 생성되는 도메인 모델
    - ActionTypeShapePolicy : 생성 시 종류별 필드 조합 검증 (domain.policy)
    - ActionRepository      : 생성된 액션 저장
*/
public interface ActionDistributionPort {
}
