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
     * 새 블록을 QUEUED 상태로 만든다(10분/40청크 자동 트리거 전용, cap 소유 오케스트레이션이
     * 호출한다 — CreateSttBlockPort 경유).
     *
     * markQueuedForRetry와 달리 CAS(compare-and-set)가 필요 없다 — 이 블록 자리를 "처음" 만드는
     * 것이라 경합할 기존 행 자체가 없다(회의당 트리거는 cap의 청크 카운터가 순차적으로만 발화시킨다).
     *
     * provider/providerJobName을 생성 시점에 이미 확정해 받는다 — 호출자(SttBlockCreationService)가
     * 이 값으로 곧바로 SttJobPort.submit()을 부를 것이므로, 여기서 다시 조회해 잡 이름을 짓지 않는다.
     *
     * @param cutReason 문자열로 받는다 — cap이 SttCutReason enum(이쪽 도메인 소유)에 의존하지
     *                  않기 위함이다(CapSttBlockReferenceEntity가 status를 String으로 읽는 것과
     *                  같은 이유). 알 수 없는 값이면 IllegalArgumentException.
     * @return 새로 만들어진 블록의 id
     */
    long createQueued(long meetingId, int blockSeq, int startOffsetMs, int endOffsetMs,
                      String cutReason, String audioS3Key, String provider, String providerJobName);

    /*
     * 재처리를 접수한다 — 상태를 QUEUED 로 되돌리고 시도 횟수를 올린다.
     *
     * <h2>읽은 값이 그대로일 때만 바꾼다 (compare-and-set)</h2>
     * 조회와 갱신 사이에 다른 재처리 요청이 끼어들 수 있다. 둘이 같은 FAILED 스냅샷을 읽으면
     * **같은 retryCount 로 같은 잡 이름을 만들고 둘 다 제출한다** — 계정 내 중복 이름이라
     * 두 번째가 거절되는데, 그건 이 코드가 잡 이름에 횟수를 넣어 막으려던 바로 그 상황이다
     * (CodeRabbit PR #223 지적).
     *
     * 그래서 **쓰기 잠금을 걸고 상태와 시도 횟수를 다시 확인한 뒤** 바꾼다. 진 쪽은 false 를
     * 받고 제출하지 않는다 — 계층 잠금이 같은 자리를 같은 방식으로 막는다(AnalysisLayerLockAcquirer).
     *
     * @param expectedRetryCount 조회 시점의 시도 횟수. 그 사이에 누가 올렸으면 내 잡 이름은
     *                           이미 남의 것과 겹치므로 바꾸지 않는다
     * @param providerJobName    **계정 내 유일해야 한다.** 같은 이름을 다시 쓰면 제출이
     *                           거절된다 — UNIQUE 가 그 실수를 DB 에서 한 번 더 잡는다
     * @return 내가 전이시켰으면 true. false 면 다른 요청이 먼저 가져갔다
     */
    boolean markQueuedForRetry(long blockId, int expectedRetryCount, String provider,
                               String providerJobName);

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
