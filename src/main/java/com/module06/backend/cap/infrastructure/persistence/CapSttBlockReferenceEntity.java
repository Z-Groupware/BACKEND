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
    이태연(capture) 소유 stt_block 테이블을 읽기 전용으로 조회하기 위한 참조 엔티티. 녹음 삭제(CAP-15)의
    "모든 STT 블록이 끝났는가" 판정에만 status를 본다. cap은 capture 코드를 import하지 않고, meeting을
    CapMeetingReferenceEntity로 읽는 것과 동일하게 필요한 테이블을 자기 read-model로 직접 매핑한다.

    ⚠️ Cap 접두어 + status는 문자열: 두 도메인이 같은 DB 테이블을 매핑하므로 엔티티명이 겹치면 컨텍스트가
    죽는다. status ENUM은 capture의 LayerStatus/도메인 enum에 의존하지 않으려고 String으로 읽는다.
    쓰기 금지(@Immutable) — 스키마 진화(ALTER)는 이태연 레인 몫이고 cap은 읽기만 한다.
*/
@Entity(name = "CapSttBlockReference")
@Table(name = "stt_block")
@Immutable
@Getter
@NoArgsConstructor
public class CapSttBlockReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "status")
    private String status;
}
