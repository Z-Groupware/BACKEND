package com.module06.backend.capture.application.port.out;

import java.util.List;
import java.util.Optional;

import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.domain.model.SttCutReason;

/*
 * stt_block(V5.4) 접근 포트다. STT-03(조회)과 STT-04(재처리)가 함께 쓴다.
 *
 * <h2>이 표는 두 도메인이 함께 본다</h2>
 * cap(녹음·업로드)이 삭제 판정에 status 를 읽는다({@code CapSttBlockReferenceEntity}, 읽기 전용).
 * **스키마를 바꾸는 것은 이쪽 레인의 몫**이고, 그쪽은 읽기만 한다 — 컬럼을 지우거나 ENUM 값을
 * 바꾸면 그쪽 매핑이 조용히 깨지므로 변경 시 공유가 필요하다.
 */
public interface SttBlockRepository {

    /*
     * 회의의 블록을 순서대로 준다(STT-03).
     *
     * companyId 를 **인자로 받는다.** stt_block 에는 회사 컬럼이 없어 조회 조건으로 막을 수
     * 없고, 관문(MeetingAccessGuard)이 먼저 지나야 한다 — 그 관문이 유일한 방어선이다.
     */
    List<SttBlockView> findByMeeting(long meetingId);

    /* 재처리할 블록 하나(STT-04). 그 회의의 블록이 아니면 비어 있다. */
    Optional<SttBlockView> findOne(long meetingId, int blockSeq);

    /*
     * 재처리를 접수한다 — 상태를 QUEUED 로 되돌리고 시도 횟수를 올린다.
     *
     * @param providerJobName **계정 내 유일해야 한다.** AWS Transcribe 잡 이름이 그렇고, 같은
     *                        이름을 다시 쓰면 제출이 거절된다. 그래서 retryCount 를 이름에
     *                        넣는다(meeting-500-block-3-r3) — UNIQUE 가 그 실수를 DB 에서 잡는다
     * @return 올라간 뒤의 시도 횟수. 응답에 그대로 실린다
     */
    int markQueuedForRetry(long blockId, String provider, String providerJobName);

    /*
     * 블록 하나의 상태. 화면(STT-03)이 쓰는 값 그대로다.
     *
     * @param error      실패 사유 코드. **사용자에게 그대로 노출하지 않는다**(V5.4 주석) —
     *                   제공자 메시지가 섞이면 되돌릴 수 없다. 화면은 이 코드로 문구를 고른다
     * @param audioS3Key 이 블록의 오디오. 재제출에 필요하다 — **두 EC2 사이 파일 전달은 S3
     *                   경유만**이라(V5.4 주석) 이 값이 없으면 다시 돌릴 대상 자체가 없다.
     *                   화면에는 내려주지 않는다(내부 저장 위치다)
     */
    record SttBlockView(
            long id,
            int blockSeq,
            int startOffsetMs,
            int endOffsetMs,
            SttBlockStatus status,
            String provider,
            SttCutReason cutReason,
            int retryCount,
            String error,
            String audioS3Key
    ) {
    }
}
