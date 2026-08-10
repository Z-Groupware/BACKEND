package com.module06.backend.cap.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    D(회의) 소유 capture_session 테이블을 읽기 전용으로 참조하기 위한 read-model
    (CapMeetingReferenceEntity와 동일 패턴). 쓰기 금지 — @Immutable로 dirty checking 자체를 막는다.

    용도: 10분/40청크 블록 자동 트리거가 세는 청크 개수에 일시정지 구간이 끼어들지 않도록,
    청크 완료 통보(CAP-07) 시점에 이 세션이 PAUSED인지 확인한다(명세 "10분 카운터는 일시정지
    구간을 빼고 이어서 센다").

    ⚠️ 이름에 Cap 접두어를 두는 이유: 같은 capture_session 테이블을 D(회의) 도메인이 이미
    write 모델(CaptureSession)로 매핑하고 있다. 클래스 단순명이 겹치면 JPA 엔티티명이 충돌해
    (Duplicate entity mapping) 스프링 컨텍스트가 통째로 죽는다 — CapMeetingReferenceEntity 도입 때
    실제로 겪은 사고와 같은 종류라 처음부터 프리픽스로 분리한다.
*/
@Entity(name = "CapCaptureSessionReference")
@Table(name = "capture_session")
@Immutable
@Getter
@NoArgsConstructor
public class CapCaptureSessionReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "meeting_id")
    private Long meetingId;

    // capture_session.status ENUM('ACTIVE','PAUSED','ENDED')을 문자열로 읽는다 — PAUSED 여부만
    // 확인하면 되므로 D의 상태 enum에 의존하지 않고 read-model 안에서 문자열로 다룬다.
    @Column(name = "status")
    private String status;
}
