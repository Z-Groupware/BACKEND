package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.GetProjectTimelineUseCase;

/* comment.
    FR-PJ-05 프로젝트 타임라인 탭 조회 구현체. 읽기 전용 트랜잭션이다.
    이 프로젝트에 하달된 모든 팀의 팀 액션을 카드 목록으로 만든다(한 팀이 여러 개 가질 수 있다).
    action 도메인 데이터를 읽지만 엔티티를 직접 참조하지 않는다 — id 기준 조회다(0절 1항).

    연결된 클래스
    - GetProjectTimelineUseCase   : 구현하는 계약
    - ProjectTimelineItemResponse : 출력 DTO (presentation, 미생성)
    - TeamReferenceEntity         : 부서명 조인 전용 (infrastructure.persistence, 미생성)
*/
public class GetProjectTimelineService implements GetProjectTimelineUseCase {
}
