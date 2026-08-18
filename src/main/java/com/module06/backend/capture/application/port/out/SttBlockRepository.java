package com.module06.backend.capture.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
     * 아직 결과가 확정되지 않은 블록 수 — PENDING · QUEUED · RUNNING 셋을 센다.
     * 0 이면 이 회의의 받아쓰기가 더 나아갈 데 없이 끝난 것이다.
     *
     * <h2>FAILED 를 세지 않는다</h2>
     * FAILED 는 **끝난 상태**다. 사람이 STT-04 를 눌러야 다시 도는 것이고, 저절로 DONE 이
     * 되지 않는다. 이걸 "미완"으로 세면 실패한 블록 하나가 회의의 분석을 영구히 막는다 —
     * 구멍이 있는 회의도 분석은 돌고, 그 구멍은 stt_gap 이 분배 확정 관문에서 막는 것이
     * 이 저장소가 고른 방향이다(SttGapRepository 주석).
     *
     * <h2>블록이 0개면 0 이다</h2>
     * 자동 트리거가 아직 한 번도 발화하지 않은 회의(또는 STT 경로 자체가 없는 회의)는 0 을
     * 받아 관문을 그대로 지난다. 그 회의는 발화 0건 검사에서 걸러지므로 여기서 막을 것이 없다 —
     * 여기서 막으면 "받아쓰기가 안 붙은 회의"와 "받아쓰기가 도는 중인 회의"가 같은 사유로
     * 생략되어 어느 쪽인지 구분할 수 없게 된다.
     */
    int countUnfinished(long meetingId);

    /*
     * 회의에 블록이 하나 이상 있고 그 블록이 모두 DONE 인지 확인한다.
     *
     * countUnfinished 는 FAILED 를 의도적으로 제외하므로 0 만 보고 자동 분석을 시작하면 안 된다.
     * 이 판정은 실패 블록이 하나라도 있으면 false 를 반환해, 일부 전사만으로 요약·액션이 만들어지는
     * 것을 막는다.
     */
    default boolean areAllDone(long meetingId) {
        List<SttBlockView> blocks = findByMeeting(meetingId);
        return !blocks.isEmpty()
                && blocks.stream().allMatch(block -> block.status() == SttBlockStatus.DONE);
    }

    /*
     * 받아쓰기가 아직 도는 중인 회의만 골라낸다(MEET-04 요약 상태 배치 조회).
     *
     * "미완"의 정의는 {@link #countUnfinished} 와 **같다** — PENDING · QUEUED · RUNNING.
     * 같은 상태 집합을 두 곳에서 따로 적으면 분석 시작 관문과 화면 문구가 갈린다: 관문은
     * 막고 있는데 화면은 「AI 요약 없음」이라고 말하는 상태가 정확히 그것이다.
     *
     * <h2>개수를 돌려주지 않는다</h2>
     * 호출자가 묻는 것은 "기다리는 중인가"이고 몇 개인지가 아니다. 개수를 돌려주면 화면이
     * 「남은 블록 3개」를 그리게 되는데, 블록 수는 회의 길이에 따라 달라지는 내부 단위라
     * 사람에게 진척을 뜻하지 않는다(남은 시간은 STT-03·SttProgress 가 준다).
     *
     * @param meetingIds 확인할 회의. null 이거나 비면 빈 집합
     * @return 미완 블록이 하나라도 있는 회의의 id. 나머지는 담기지 않는다
     */
    Set<Long> findMeetingsWithUnfinishedBlocks(List<Long> meetingIds);

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
     * 제출된 잡 이름을 제공자가 실제로 접수한 값으로 고친다.
     *
     * <h2>왜 이런 갱신이 필요한가</h2>
     * 잡 이름은 제출 <b>전에</b> 정해져 행에 들어간다(createQueued · markQueuedForRetry). 그런데
     * 그 이름이 제공자 쪽에서 이미 <b>다른 오디오</b>에 쓰이고 있으면 그 이름으로는 영영 제출할
     * 수 없어서, 어댑터가 새 이름으로 접수시키고 그 값을 돌려준다(SttJobPort#submit).
     *
     * 그때 이 행을 안 고치면 <b>우리가 만든 잡을 우리가 못 찾는다</b> — 폴링은 저장된 이름으로
     * 조회하므로(SttJobResultPort#fetch) 없는 이름을 물어보고 UNKNOWN 만 받는다. 잡은 정상적으로
     * 돌아 요금까지 나가는데 결과는 영영 안 실린다.
     *
     * 상태는 건드리지 않는다. 이름만 바뀐 것이고 그 블록은 여전히 방금 제출된 QUEUED 다.
     */
    void updateProviderJobName(long blockId, String providerJobName);

    /*
     * 아직 끝나지 않은 블록(QUEUED·RUNNING)을 폴링 워커에게 준다.
     *
     * 회의를 가리지 않는다 — 워커는 특정 회의의 요청을 처리하는 것이 아니라 제출해 둔 잡 전부를
     * 훑는다. 그래서 회사 관문도 지나지 않는다(사람의 요청이 아니다).
     *
     * FAILED 는 담지 않는다. **끝난 상태**이고 사람이 STT-04 를 눌러야 다시 QUEUED 가 된다 —
     * 워커가 실패한 잡을 계속 물어보면 제공자 호출만 늘고 상태는 안 바뀐다.
     *
     * @param limit 한 주기에 볼 최대 건수. 상한이 없으면 밀린 잡이 많을 때 한 주기가 끝나지
     *              않고, fixedDelay 의 겹침 방어가 의미를 잃는다
     */
    List<PendingBlock> findUnfinished(int limit);

    /*
     * 제공자가 돌리기 시작했다 — QUEUED 인 행만 옮긴다.
     *
     * 상태를 조건에 넣는 이유는 markQueuedForRetry 와 같다. 폴링과 재처리가 같은 행을 동시에
     * 만질 수 있고, 사람이 방금 재처리를 눌러 QUEUED 로 되돌린 행을 워커가 옛 잡의 RUNNING 으로
     * 덮으면 **새 잡의 결과를 기다리는 자리가 사라진다.**
     *
     * @return 내가 옮겼으면 true. false 면 그 사이에 상태가 바뀌었다
     */
    boolean markRunning(long blockId);

    /*
     * 인식이 끝났고 정본까지 적재됐다.
     *
     * ⚠ **적재 뒤에만 부른다.** 먼저 닫으면 분석 시작 관문이 통과되고 전사가 빈 회의가 분석에
     * 들어간다(SttBlockJpaEntity#markDone 주석).
     *
     * QUEUED·RUNNING 에서만 옮긴다 — 이미 FAILED 로 닫힌 행을 뒤늦게 DONE 으로 되살리면
     * 사람이 재처리로 만든 새 잡과 결과가 겹친다.
     */
    boolean markDone(long blockId);

    /* 실패로 닫는다. STT-04 의 대상이 된다. QUEUED·RUNNING 에서만 옮긴다. */
    boolean markFailed(long blockId, String errorCode);

    /*
     * 길이를 모른 채 만들어진 블록의 끝 오프셋을 채운다(**duration 복구**).
     *
     * <h2>수동 업로드(CAP-10)가 이 자리를 만든다</h2>
     * 자동 블록은 트리거가 절단점을 알고 만들므로 구간이 처음부터 정확하다. 그런데 수동 업로드는
     * 사용자가 올린 파일 하나가 통째로 블록 하나이고(WHOLE_FILE), **업로드 시점에는 길이를 모른다** —
     * CAP-10 응답의 durationMs 가 0 인 이유이고, 그쪽 주석이 "파이프라인이 async 로 채운다"고
     * 적어 둔 자리다.
     *
     * 인식이 끝나면 마지막 단어의 끝 시각이 곧 그 오디오의 길이다. 그걸로 채운다.
     *
     * <h2>이미 값이 있으면 덮지 않는다</h2>
     * 자동 블록의 구간은 VAD 절단점이 정한 사실이고, 인식 결과로 그걸 덮으면 **블록 경계가
     * 조용히 움직인다** — 뒤 블록의 시작과 맞지 않게 되고, 그 어긋남이 정본 오프셋에 그대로
     * 실린다. 그래서 "0 이거나 시작보다 작은" 블록만 채운다.
     *
     * @return 채웠으면 true. 이미 값이 있었거나 그 블록이 없으면 false
     */
    boolean recoverAudioSpan(long blockId, int endOffsetMs);

    /*
     * 폴링이 잡 하나를 되짚는 데 필요한 것만 담는다.
     *
     * @param startOffsetMs 회의 기준 블록 시작. 제공자가 주는 오프셋은 블록 기준이라 이 값을
     *                      더해야 회의 좌표가 된다 — 빠뜨리면 두 번째 블록부터 발화가 회의
     *                      맨 앞으로 겹쳐 쌓인다
     * @param endOffsetMs   회의 기준 블록 끝. 실패했을 때 **구멍 구간**이 이 둘이다 —
     *                      "아무도 못 들은 구간"을 남기려면 끝을 알아야 한다(stt_gap · V5.5)
     */
    record PendingBlock(
            long id,
            long meetingId,
            int blockSeq,
            SttBlockStatus status,
            String provider,
            String providerJobName,
            int startOffsetMs,
            int endOffsetMs,
            /*
             * 이 블록이 만들어진 시각 = 제출 시각이다. **얼마나 오래 안 끝났는지**를 재는 기준이고,
             * 폴링이 제공자를 못 읽는 상태(UNAVAILABLE)가 영구화됐는지 판단하는 데 쓴다.
             *
             * startedAt 을 쓰지 않는 이유 — 그 값은 **제공자가 RUNNING 으로 잡았을 때만** 찍힌다
             * (SttBlockJpaEntity#markRunning). 제출 직후 바로 COMPLETED 가 된 잡은 RUNNING 을
             * 거치지 않아 NULL 로 남고, 그러면 정작 오래 갇힌 블록에서 기준이 비어 있다.
             */
            LocalDateTime createdAt
    ) {
    }

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
            String audioS3Key,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {

        /* 이 블록이 담은 오디오 길이. 남은 시간 추정이 쓴다(회의 시계가 아니라 처리할 양이다). */
        public int audioMs() {
            return Math.max(0, endOffsetMs - startOffsetMs);
        }
    }
}
