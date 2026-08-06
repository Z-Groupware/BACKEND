package com.module06.backend.capture.infrastructure.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * caption_chunk(V5.2) **읽기 전용** 매핑이다.
 *
 * ⚠ 이 테이블의 주인은 김현지(CAP)다. CAP-11 이 쓴다. 여기서는 L1 화자 귀속의 판정 근거로
 * 읽기만 하므로 세터도 정적 팩토리도 두지 않는다 — 두면 A 가 남의 테이블에 쓰는 경로가 생긴다.
 *
 * 필요한 컬럼만 매핑한다. text 는 매핑하지 않는다 — L1 은 **누가 말했는지**만 판정하고
 * 무엇을 말했는지는 정본(transcript_chunk)에서 온다. 자막 텍스트는 부정확해서 내용으로
 * 쓰지 않기로 한 값이고, 매핑해 두면 언젠가 그걸 내용으로 쓰는 코드가 붙는다.
 *
 * rms 를 BigDecimal 로 받는다. 컬럼이 DECIMAL(6,2) 이고, 3dB 임계값 비교가 부동소수 오차에
 * 걸리면 판정 포기와 확정의 경계가 흔들린다.
 */
@Entity
@Table(name = "caption_chunk")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaptionChunkJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* 발화자. CAP-11 이 토큰에서 꺼내 넣는다 — 본문으로 받으면 남의 이름으로 발화를 심을 수 있다. */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "start_offset_ms", nullable = false)
    private Integer startOffsetMs;

    @Column(name = "end_offset_ms", nullable = false)
    private Integer endOffsetMs;

    /* 구간 마이크 음량(dBFS, 음수). 화자 판정의 유일한 근거이고 NOT NULL 이다. */
    @Column(name = "rms", nullable = false, precision = 6, scale = 2)
    private BigDecimal rms;
}
