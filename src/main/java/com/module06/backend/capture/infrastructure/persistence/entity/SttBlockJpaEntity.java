package com.module06.backend.capture.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.domain.model.SttCutReason;

/*
 * stt_block(V5.4) 매핑이다.
 *
 * <h2>⚠ 엔티티 이름을 반드시 다르게 준다</h2>
 * 같은 테이블을 cap 도 매핑한다({@code CapSttBlockReferenceEntity}, 읽기 전용). **엔티티명이
 * 겹치면 컨텍스트가 뜨지 않는다** — 그쪽 주석에 적힌 그대로다. 그래서 이쪽은 클래스명이
 * 다르고(SttBlock…), 이름이 같아질 여지를 없애려고 {@code @Entity(name)} 도 명시한다.
 *
 * 역할도 갈린다 — cap 은 삭제 판정에 status 만 읽고(@Immutable), **쓰기와 스키마 진화는 이쪽
 * 레인의 몫**이다.
 *
 * <h2>status·cut_reason 을 enum 으로 매핑한다</h2>
 * cap 쪽은 String 으로 읽는다(그쪽이 capture 도메인에 의존하지 않으려고). 여기서는 값의 뜻이
 * 곧 판정이라 enum 이 맞다 — 재처리 대상이 FAILED 뿐이라는 규칙을 문자열 비교로 두면 오타가
 * 컴파일을 지나간다.
 */
@Entity(name = "CaptureSttBlock")
@Table(name = "stt_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SttBlockJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* 회의 내 순번(0부터). 화면과 STT-04 의 path variable 이 이 값이다. */
    @Column(name = "block_seq", nullable = false)
    private int blockSeq;

    @Column(name = "start_offset_ms", nullable = false)
    private int startOffsetMs;

    @Column(name = "end_offset_ms", nullable = false)
    private int endOffsetMs;

    /* FALLBACK_OVERLAP 인 블록은 경계에서 발화가 잘렸을 수 있다 — 품질 조사의 출발점이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cut_reason", nullable = false)
    private SttCutReason cutReason;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    /* 계정 내 유일해야 한다. retryCount 가 들어 있다(meeting-500-block-3-r3). */
    @Column(name = "provider_job_name", length = 200)
    private String providerJobName;

    /* 블록 오디오. 두 EC2 사이 파일 전달은 S3 경유만이라, 없으면 재제출할 대상이 없다. */
    @Column(name = "audio_s3_key", length = 1024)
    private String audioS3Key;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SttBlockStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /* JOB_FAILED 등. 사용자에게 그대로 노출하지 않는다 — 화면은 이 코드로 문구를 고른다. */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /*
     * 제출 시각. **읽기 전용으로 매핑한다** — DB 기본값(CURRENT_TIMESTAMP)이 채우는 값이고,
     * 우리가 실으면 재처리로 같은 행을 되돌릴 때 시각이 밀려 "이 블록이 얼마나 오래 안 끝났나"의
     * 기준이 사라진다.
     *
     * 폴링이 제공자를 못 읽는 상태가 영구화됐는지 판단하는 데 쓴다(SttResultPollingService).
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * 새 블록을 QUEUED로 만든다(10분/40청크 자동 트리거 전용). retry()의 markQueuedForRetry와
     * 달리 이전 실패 흔적을 지울 필요가 없다 — 처음 만드는 행이라 지울 과거 자체가 없다.
     */
    public static SttBlockJpaEntity createQueued(long meetingId, int blockSeq, int startOffsetMs, int endOffsetMs,
                                                 SttCutReason cutReason, String audioS3Key, String provider,
                                                 String providerJobName) {
        SttBlockJpaEntity entity = new SttBlockJpaEntity();
        entity.meetingId = meetingId;
        entity.blockSeq = blockSeq;
        entity.startOffsetMs = startOffsetMs;
        entity.endOffsetMs = endOffsetMs;
        entity.cutReason = cutReason;
        entity.audioS3Key = audioS3Key;
        entity.provider = provider;
        entity.providerJobName = providerJobName;
        entity.status = SttBlockStatus.QUEUED;
        entity.retryCount = 0;
        return entity;
    }

    /*
     * 재처리를 접수한다(STT-04).
     *
     * 이전 실패의 흔적을 지운다 — errorCode 와 finishedAt 이 남아 있으면 이번 시도가 아직
     * 돌고 있는데도 화면이 **지난 실패 사유를 이번 것으로 보여준다.** 반대로 retryCount 는
     * 누적한다: 몇 번이나 실패했는지가 곧 이 블록을 포기할지 판단하는 근거다.
     *
     * @return 올라간 뒤의 시도 횟수. 잡 이름에 이미 들어간 값과 같아야 한다
     */
    public int markQueuedForRetry(String provider, String providerJobName) {
        this.status = SttBlockStatus.QUEUED;
        this.retryCount += 1;
        this.provider = provider;
        this.providerJobName = providerJobName;
        this.errorCode = null;
        this.startedAt = null;
        this.finishedAt = null;
        return this.retryCount;
    }

    /*
     * 제공자가 실제로 돌리기 시작했다(폴링이 확인).
     *
     * startedAt 을 여기서 찍는다 — 제출 시각이 아니라 **제공자가 잡은 시각**이다. 둘을 섞으면
     * 큐에서 기다린 시간과 인식에 걸린 시간이 한 값에 뭉쳐, 느린 원인이 우리 쪽인지 제공자
     * 쪽인지 가를 수 없다.
     *
     * 이미 찍혀 있으면 덮지 않는다. 폴링은 같은 블록을 여러 주기 동안 RUNNING 으로 보므로,
     * 매번 덮으면 startedAt 이 계속 밀려 "방금 시작한 잡"으로 보인다.
     */
    public void markRunning(LocalDateTime now) {
        this.status = SttBlockStatus.RUNNING;
        if (this.startedAt == null) {
            this.startedAt = now;
        }
    }

    /*
     * 인식이 끝났고 **정본까지 적재됐다.**
     *
     * ⚠ 이 전이는 적재 뒤에만 불러야 한다. 먼저 DONE 으로 닫으면 분석 시작 관문
     * (AnalysisOrchestrator 의 미완 블록 검사)이 통과되고, 전사가 비어 있는 회의가 분석에
     * 들어간다 — 그 결과가 "분석 완료"로 기록되는 것이 이 파이프라인에서 가장 위험한 실패다.
     */
    public void markDone(LocalDateTime now) {
        this.status = SttBlockStatus.DONE;
        this.errorCode = null;
        this.finishedAt = now;
    }

    /*
     * 실패로 닫는다. **STT-04 의 유일한 대상이 된다.**
     *
     * errorCode 는 화면이 문구를 고르는 값이고 제공자 메시지를 그대로 넣지 않는다(V5.4 주석) —
     * 제공자 문자열은 언제든 바뀌고, 바뀌는 문자열에 화면이 붙으면 되돌릴 수 없다.
     */
    public void markFailed(String errorCode, LocalDateTime now) {
        this.status = SttBlockStatus.FAILED;
        this.errorCode = errorCode;
        this.finishedAt = now;
    }

    /*
     * 길이를 모른 채 만들어진 블록의 끝을 채운다(duration 복구 · 수동 업로드).
     *
     * **이미 값이 있으면 덮지 않는다.** 자동 블록의 구간은 VAD 절단점이 정한 사실이고, 인식
     * 결과로 덮으면 블록 경계가 조용히 움직여 뒤 블록의 시작과 맞지 않게 된다.
     *
     * @return 채웠으면 true
     */
    public boolean recoverAudioSpan(int endOffsetMs) {
        if (this.endOffsetMs > this.startOffsetMs) {
            return false;
        }
        if (endOffsetMs <= this.startOffsetMs) {
            // 인식 결과가 0 이거나 시작보다 앞이다. 채워도 길이가 없어 의미가 없다.
            return false;
        }
        this.endOffsetMs = endOffsetMs;
        return true;
    }
}
