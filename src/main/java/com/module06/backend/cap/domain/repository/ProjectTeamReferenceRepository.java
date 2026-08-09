package com.module06.backend.cap.domain.repository;

/* comment.
    project(project 도메인) 소유 project_team 테이블 읽기 전용 조회 계약. access-guard가 "이 회의의
    프로젝트에 이 팀이 배정돼 있는가"(프로젝트 멤버 열람권한)를 판정하는 데 쓴다.
*/
public interface ProjectTeamReferenceRepository {

    /** 이 프로젝트에 이 팀이 배정돼 있는지. */
    boolean isTeamAssignedToProject(Long projectId, Long teamId);
}
