package com.module06.backend.cap.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    project(project 도메인) 소유 project_team 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    "이 회의의 프로젝트에 이 팀이 배정돼 있는가"만 존재 여부로 확인하는 용도라 복합키 외 컬럼은 없다
    (CapMeetingAttendeeReferenceEntity와 동일 패턴).

    ⚠️ Cap 접두어 이유: project 도메인이 이미 같은 project_team 테이블을 매핑하는
    ProjectTeamJpaEntity(쓰기 모델)를 가진다. 엔티티명이 겹치면 컨텍스트가 죽으므로
    도메인 프리픽스로 분리한다.
*/
@Entity(name = "CapProjectTeamReference")
@Table(name = "project_team")
@Immutable
@Getter
@NoArgsConstructor
public class CapProjectTeamReferenceEntity {

    @EmbeddedId
    private CapProjectTeamId id;
}
