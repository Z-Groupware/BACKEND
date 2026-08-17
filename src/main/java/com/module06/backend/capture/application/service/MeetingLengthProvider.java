package com.module06.backend.capture.application.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/*
 * 회의가 실제로 몇 분이었는지 읽는다. **자동 분석의 비용 관문**이 이 값을 본다.
 *
 * 예정 시간(start_at ~ end_at)이 아니라 실측(started_at ~ ended_at)이다. 30분으로 잡아두고
 * 2분 만에 끝난 회의가 예정으로는 30분이라, 예정을 보면 관문이 아무것도 거르지 못한다.
 *
 * 회의 정보의 주인은 D(회의) 도메인이다. 여기서는 읽기만 한다 —
 * {@link MeetingDateProvider} · {@link MeetingParticipantProvider} 와 같은 방식이다.
 */
public interface MeetingLengthProvider {

    /*
     * @return 실제 시작~종료 길이. 회의가 없거나 두 시각 중 하나라도 비어 있으면 **비어 있다.**
     *         0 이나 임의값으로 채우지 않는다 — 모르는 것과 짧은 것은 다르고, 모르는 값을
     *         짧다고 읽으면 멀쩡한 회의의 분석이 조용히 건너뛰어진다.
     */
    Optional<Duration> actualLengthOf(long meetingId);

    default Optional<Boolean> isOnline(long meetingId) {
        return Optional.empty();
    }

    /*
     * 여러 회의의 길이·비대면 여부를 **한 번에** 읽는다.
     *
     * 목록 화면이 회의마다 actualLengthOf + isOnline 을 부르면 회의 수 × 2 쿼리가 된다
     * (AnalysisLayerRepository#findStatesByMeetings 가 배치를 만든 것과 같은 이유). 두 값을
     * 한 레코드로 묶는 이유는 같은 행에서 읽히기 때문이다 — 따로 두면 배치도 두 번이 된다.
     *
     * 기본 구현은 단건 조회를 반복한다. 테스트 대역과 이 인터페이스의 다른 구현이 배치 SQL 을
     * 따로 쓰지 않아도 동작하게 하기 위함이고, 운영 경로(MeetingLengthJdbcProvider)는 재정의한다.
     *
     * @return 회의 id → 읽은 값. 회의를 찾지 못하면 그 id 는 담기지 않는다
     */
    default Map<Long, MeetingLength> lengthsOf(List<Long> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MeetingLength> result = new LinkedHashMap<>();
        meetingIds.stream().filter(Objects::nonNull).distinct().forEach(meetingId ->
                result.put(meetingId, new MeetingLength(
                        actualLengthOf(meetingId), isOnline(meetingId).orElse(false))));
        return result;
    }

    /*
     * 하한 판정에 필요한 회의 한 건의 사실 둘.
     *
     * @param length 실측 길이. 모르면 비어 있다 — 0 과 구분해야 한다(actualLengthOf 주석)
     * @param online 비대면 회의인가. 모르면 false — 하한을 적용하는 쪽이 기본이고,
     *               비대면 면제는 그렇다고 확인됐을 때만 준다
     */
    record MeetingLength(Optional<Duration> length, boolean online) {
    }
}
