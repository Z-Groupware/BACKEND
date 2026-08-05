package com.module06.backend.cap.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    D(회의) 소유 meeting 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티(project의
    TeamReferenceEntity와 동일 패턴). 쓰기 금지 — @Immutable로 dirty checking 자체를 막는다.
    D의 capture_session 애그리거트가 아직 없어도, 이미 V1에 존재하는 meeting 테이블로
    "회의 존재/companyId/hostMemberId" 검증은 지금 바로 가능하다.
*/
@Entity
@Table(name = "meeting")
@Immutable
@Getter
@NoArgsConstructor
public class MeetingReferenceEntity {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "host_member_id")
    private Long hostMemberId;
}
