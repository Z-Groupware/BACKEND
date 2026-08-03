package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.CreateProjectUseCase;

/* comment.
    FR-PJ-01 프로젝트 생성 유스케이스 구현체. 쓰기 트랜잭션 경계를 가진다.
    흐름: OWNER 권한 검사 → 태그 중복·형식 검증 → Project 생성 → 지정 부서 연결 → 저장.

    연결된 클래스
    - CreateProjectUseCase   : 구현하는 계약
    - CreateProjectCommand   : 입력
    - ProjectOwnerOnlyPolicy : 권한 검사
    - ProjectRepository      : 저장
*/
public class CreateProjectService implements CreateProjectUseCase {
}
