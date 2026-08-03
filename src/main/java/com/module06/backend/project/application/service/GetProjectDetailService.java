package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.GetProjectDetailUseCase;

/* comment.
    FR-PJ-02 프로젝트 상세(기획 탭) 조회 구현체. 읽기 전용 트랜잭션이다.
    기획(description)과 첨부파일 목록을 한 응답에 담아 FE의 추가 왕복을 없앤다.

    연결된 클래스
    - GetProjectDetailUseCase     : 구현하는 계약
    - ProjectRepository           : 프로젝트 조회
    - ProjectAttachmentRepository : 첨부파일 목록 조회
    - ProjectDetailResponse       : 출력 DTO (presentation)
*/
public class GetProjectDetailService implements GetProjectDetailUseCase {
}
