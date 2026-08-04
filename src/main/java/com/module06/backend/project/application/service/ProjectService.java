package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.BulkUpdateProjectStatusUseCase;
import com.module06.backend.project.application.usecase.CreateProjectUseCase;
import com.module06.backend.project.application.usecase.GetProjectDetailUseCase;
import com.module06.backend.project.application.usecase.GetProjectListUseCase;
import com.module06.backend.project.application.usecase.GetProjectTimelineUseCase;
import com.module06.backend.project.application.usecase.UpdateProjectUseCase;

/* comment.
    프로젝트 리소스(FR-PJ-01,02,03,06,07)를 다루는 단일 구현체. 쓰기·읽기 트랜잭션 경계는
    메서드별로 갈린다(create·update·bulkUpdateStatus는 쓰기, list·detail·timeline은 읽기 전용).
    UseCase 인터페이스는 엔드포인트 1:1(포트 경계)로 유지하되, 구현체는 같은 애그리거트를
    다루는 것끼리 이 클래스 하나로 묶었다 — 08/04 팀 협의(윤종호)로 서비스 클래스 파편화를 줄이는 쪽으로 확정.

    연결된 클래스
    - CreateProjectUseCase · UpdateProjectUseCase · GetProjectListUseCase ·
      GetProjectDetailUseCase · BulkUpdateProjectStatusUseCase · GetProjectTimelineUseCase : 구현하는 계약
    - ProjectOwnerOnlyPolicy    : 생성·수정·상태변경 시 권한 검사
    - ProjectTagImmutablePolicy : 수정 시 태그 불변 검사 (domain.policy)
    - ProjectRepository         : 저장·조회
*/
public class ProjectService implements
        CreateProjectUseCase,
        UpdateProjectUseCase,
        GetProjectListUseCase,
        GetProjectDetailUseCase,
        BulkUpdateProjectStatusUseCase,
        GetProjectTimelineUseCase {
}
