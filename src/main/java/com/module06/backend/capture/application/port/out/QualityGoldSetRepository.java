package com.module06.backend.capture.application.port.out;

import java.time.LocalDateTime;

/*
 * quality_gold_set(V5.11) 접근 포트다. QLTY-01(등록)이 쓰고 QLTY-02(지표)가 읽는다.
 *
 * <h2>동결이 이 표의 전부다</h2>
 * 나중에 라벨을 손대면 이전 측정치와 비교가 불가능해진다 — "지난주 precision 0.82"를 재현할
 * 근거가 사라지고, 프롬프트를 바꿔 나아졌는지도 감으로만 남는다. 그래서 **기존 행을 고치지
 * 않는다.** 다시 라벨링하면 version 을 올려 새 행으로 쌓는다(V5.11 주석).
 */
public interface QualityGoldSetRepository {

    /*
     * 정답지를 동결한다. **항상 새 행이다** — 기존 버전은 그대로 둔다.
     *
     * @param version 이 회의의 다음 버전. UNIQUE(meeting_id, version) 가 동시 등록을 막는다
     * @throws org.springframework.dao.DataIntegrityViolationException
     *         같은 버전을 동시에 등록했을 때. 호출자가 409 로 옮긴다
     */
    GoldSetView freeze(FreezeCommand command);

    /* 이 회의의 마지막 버전. 없으면 0 이다 — 다음 등록이 1 번이 된다. */
    int latestVersionOf(long meetingId);

    record FreezeCommand(
            long companyId,
            long meetingId,
            int version,
            String labeledActions,
            String labeledItems,
            long frozenBy,
            String note
    ) {
    }

    /*
     * 동결된 결과.
     *
     * **액션 수를 담지 않는다.** 이 저장소는 라벨을 JSON 문자열로만 받아서 그 안에 몇 건이
     * 들어 있는지 모른다 — 담아 두면 값을 채울 수 없어 언제나 0 이 되고, 나중에 QLTY-02 가
     * 그 0 을 "정답 액션 수"로 읽는다. 지표의 신뢰 구간을 정하는 값이라(5건으로 잰 precision 과
     * 100건으로 잰 것은 다른 값이다) 조용히 0 이 되면 안 된다.
     *
     * 액션 수는 **라벨을 만든 쪽이 소유한다**(CodeRabbit PR #244).
     */
    record GoldSetView(long id, int version, LocalDateTime frozenAt) {
    }
}
