package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.GetProjectListUseCase;

/* comment.
    FR-PJ-02 프로젝트 목록 조회 구현체. 읽기 전용 트랜잭션이다.
    진행률·회의수·액션수·부서수는 DB 컬럼이 아니라 여기서 집계하는 파생값이다.
    목록이라 N+1이 터지기 쉬운 지점 — 집계 쿼리를 한 번에 묶어야 한다.

    연결된 클래스
    - GetProjectListUseCase  : 구현하는 계약
    - ProjectRepository      : 조회
    - ProjectSummaryResponse : 출력 DTO (presentation, 미생성)
*/
public class GetProjectListService implements GetProjectListUseCase {
}
