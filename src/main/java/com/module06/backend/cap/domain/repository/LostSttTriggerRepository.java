package com.module06.backend.cap.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.cap.domain.model.Recording;

/* comment.
    STT 트리거가 유실된 녹음을 찾는 읽기 계약(#574).

    녹음 등록은 커밋됐는데 STT 트리거가 실행되지 않은 상태를 찾는다. 그 상태는 DB에
    "stt_triggered=1 인데 stt_block 이 0건"으로만 나타난다 — 예외도 로그도 남지 않는다.
    트리거가 afterCommit 콜백(OnlineMeetingRecordingAdapter) 또는 같은 트랜잭션
    (ManualRecordingService) 안에 있어서, 프로세스가 죽거나 요청이 끊기면 그 약속이
    메모리와 함께 사라지기 때문이다.

    ProcessingCompletionRepository 와 같은 패턴이다 — 데이터는 capture 소유(stt_block)지만
    cap 이 자기 read-model 로 읽기만 한다. 스키마는 건드리지 않는다.
*/
public interface LostSttTriggerRepository {

    /*
     * 트리거가 유실된 것으로 보이는 녹음을 오래된 것부터 돌려준다.

     * <h2>왜 시간 범위를 받나 — 두 경계가 각각 다른 사고를 막는다</h2>
     * {@code createdUntil}(유예)보다 **새 녹음은 담지 않는다.** 트리거 직후에도 stt_block 은
     * 잠깐 0건이다(ManualRecordingService 주석: 그 구간을 "완료가 아니라 진행 중"으로 읽는다).
     * 유예 없이 주우면 방금 정상 등록된 녹음을 배치가 다시 제출하고, 잡 이름이
     * {@code meeting-{id}-block-0-r0} 로 고정이라 UNIQUE(provider_job_name) 위반이 난다.
     *
     * {@code createdFrom}(상한)보다 **오래된 녹음도 담지 않는다.** 재시도 횟수를 저장할 컬럼이
     * 없으므로 나이가 그 자리를 대신한다 — 영구 실패(예: 지금은 고쳐진 outputKey 400)를
     * 무한히 재시도하면 매 주기 같은 호출을 반복하고, 그 반복 역시 조용하다.
     *
     * @param createdFrom  이 시각보다 오래된 녹음은 제외(재시도 상한)
     * @param createdUntil 이 시각보다 새 녹음은 제외(유예)
     * @param limit        한 주기에 가져올 최대 건수. 한 번에 다 줍지 않는 이유는 제출이
     *                     외부 호출이라, 밀린 물량을 한 주기에 몰아 태우면 그 사이 들어온
     *                     정상 업로드가 제공자 쪽 한도에 걸린다
     * @return 오래된 것부터. 없으면 빈 목록
     */
    List<Recording> findSttTriggeredWithoutBlocks(LocalDateTime createdFrom, LocalDateTime createdUntil, int limit);
}
