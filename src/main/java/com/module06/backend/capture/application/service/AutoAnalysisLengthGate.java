package com.module06.backend.capture.application.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.service.MeetingLengthProvider.MeetingLength;

/*
 * 자동 분석의 길이 하한을 **한 곳에서** 판정한다(명세 ANLZ-01 SKIPPED_TOO_SHORT).
 *
 * <h2>왜 트리거에서 꺼냈나</h2>
 * 이 규칙은 두 곳이 알아야 한다 — 자동 실행을 막는 관문(MeetingCompletedAnalysisTrigger)과
 * 그 결과를 화면에 설명하는 조회(MeetingSummaryQueryService)다. 관문에만 두면 조회는 건너뛴
 * 회의를 「분석 시작 전」과 구분하지 못하고, 그러면 사용자는 영원히 끝나지 않는 「요약중」을
 * 본다(#572). 그렇다고 조회가 3분을 따로 적으면 두 값이 언젠가 갈리고, 그때 관문은 막는데
 * 화면은 기다리라고 말한다 — SttBlockRepository 가 "미완"의 정의를 한 곳에 모아 둔 것과 같은
 * 이유다.
 *
 * <h2>모르면 돌린다</h2>
 * 길이를 못 읽는 것과 짧은 것은 다르다. 못 읽을 때 건너뛰면 멀쩡한 회의의 분석이 조용히
 * 사라지고 사람은 그 사실조차 모른다. 반대 방향의 손해는 토큰이고, 그건 로그로 보인다.
 * 조회 실패(예외)도 같은 방향으로 간다 — DB 가 잠깐 흔들렸다는 이유로 회의가 「너무 짧음」으로
 * 표시되면 그 문구가 거짓말이 된다.
 *
 * <h2>비대면은 면제다</h2>
 * 비대면 회의는 createOnline 이 입장·종료를 같은 시각으로 저장해 길이가 **항상 0초**다
 * (커밋 1ee923ef). 하한을 적용하면 100% 걸리므로 판정 대상이 아니다. 하한이 거르려는 것은
 * "만들어놓고 스쳐 지나간 회의"인데, 파일을 직접 올린 행위는 그 반대의 의사표시다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoAnalysisLengthGate {

    /*
     * 자동 실행 하한이다. 이보다 짧은 회의는 자동으로 부르지 않는다.
     *
     * 자동 트리거는 비용 통제를 약하게 만든다 — 테스트로 만든 회의, 들어왔다 바로 나온 회의까지
     * 전부 모델을 태운다. 3분 미만에서 "누가·무엇을·언제까지"가 나올 일도 드물다.
     * 걸러진 회의도 사람이 원하면 ANLZ-01 로 돌릴 수 있다(그쪽에는 이 검사가 없다).
     */
    public static final Duration MIN_LENGTH_FOR_AUTO_RUN = Duration.ofMinutes(3);

    private final MeetingLengthProvider meetingLengthProvider;

    /* 자동 실행 관문이 쓰는 단건 판정이다. */
    public boolean tooShortForAutoRun(long meetingId) {
        MeetingLength read;
        try {
            read = new MeetingLength(meetingLengthProvider.actualLengthOf(meetingId),
                    meetingLengthProvider.isOnline(meetingId).orElse(false));
        } catch (RuntimeException e) {
            log.warn("회의 길이 조회가 실패해 하한 검사를 건너뛴다 — meetingId={}", meetingId, e);
            return false;
        }
        if (!tooShort(read)) {
            return false;
        }
        log.info("회의 종료 자동 분석 생략 — {}초짜리 회의다(하한 {}분). meetingId={}",
                read.length().map(Duration::toSeconds).orElse(0L),
                MIN_LENGTH_FOR_AUTO_RUN.toMinutes(), meetingId);
        return true;
    }

    /*
     * 조회가 쓰는 배치 판정이다. **계층 기록이 없는 회의만 넘길 것** — 이미 분석된 회의는
     * 이 값을 쓰지 않으므로 물어보면 버리는 값을 읽는 쿼리가 된다
     * (MeetingSummaryQueryService 가 findMeetingsWithUnfinishedBlocks 를 좁혀 부르는 것과 같다).
     *
     * @return 하한에 걸린 회의의 id. 나머지는 담기지 않는다
     */
    public Set<Long> findTooShortForAutoRun(List<Long> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            return Set.of();
        }
        Map<Long, MeetingLength> lengths;
        try {
            lengths = meetingLengthProvider.lengthsOf(meetingIds);
        } catch (RuntimeException e) {
            // 단건과 같은 방향이다 — 못 읽었으면 「너무 짧음」이라고 단정하지 않는다.
            log.warn("회의 길이 배치 조회가 실패해 하한 검사를 건너뛴다 — 회의 {}건", meetingIds.size(), e);
            return Set.of();
        }
        return lengths.entrySet().stream()
                .filter(entry -> tooShort(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /* 단건·배치가 같은 판정을 쓴다. 갈리면 관문과 화면 문구가 어긋난다. */
    private boolean tooShort(MeetingLength read) {
        if (read.online()) {
            return false;
        }
        Optional<Duration> length = read.length();
        if (length.isEmpty()) {
            return false;
        }
        return length.get().compareTo(MIN_LENGTH_FOR_AUTO_RUN) < 0;
    }
}
