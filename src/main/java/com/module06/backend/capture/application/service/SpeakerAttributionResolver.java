package com.module06.backend.capture.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.domain.model.CaptionChunk;
import com.module06.backend.capture.domain.model.SpeakerSource;
import com.module06.backend.capture.domain.model.Utterance;

/*
 * L1 화자 귀속의 판정 로직이다. **모델이 아니라 산수다**(명세 「L1 화자 귀속 (rms·명단으로 코드 판정)」).
 *
 * <h2>무엇을 맞추는가</h2>
 * 정본(transcript_chunk)과 자막(caption_chunk)은 성격이 정반대다.
 *   정본 = 녹음을 STT 로 받아쓴 것 — 정확하지만 **화자를 모른다**
 *   자막 = 참석자 브라우저가 보낸 것 — 부정확하지만 **화자는 확실하다**
 * 둘을 ±1.5초 시간창에서 겹쳐 보고, 그 구간 마이크 음량(rms)이 가장 큰 참석자를 화자로 정한다.
 *
 * <h2>다중 후보 비교 로직을 걷어냈다(설계 변경)</h2>
 * 녹음이 host 한 명의 기기로만 이뤄지는 걸로 확정되면서, 자막도 host만 보낸다(CaptionController
 * host-only) — capture·프론트 확인 결과 캡처 화면(webkitSpeechRecognition·MediaRecorder)이
 * 애초에 host 전용 컴포넌트 트리 하나였다. 즉 이 시간창 안에 잡히는 캡션 후보가 이제 항상 0명
 * 아니면 1명(host)이라, 여러 참석자의 rms를 순위 매겨 비교하거나(1·2등 dB 차이) 자막 안 보낸
 * 한 명을 소거법으로 지목하는 로직은 성립할 표본 자체가 없어졌다. 그 두 갈래를 제거했다 —
 * 남은 단일 후보 분기의 everyAttendeeCaptioned 게이트가, host 혼자만 캡션을 보내는 다인원
 * 회의에서는 항상 거짓이 되어 자연스럽게 판정을 포기한다(아래 클래스 주석 "판정을 포기하는
 * 것이 정상 동작이다" 그대로).
 *
 * <h2>왜 이 클래스가 Spring 의존이 없나</h2>
 * 판정이 순수 계산이라 저장소도 HTTP 도 필요 없다. 그래서 경계 입력(자막 미사용 참석자,
 * 후보 0건)을 전부 단위 테스트로 고정할 수 있다 — 이 판정이 틀리면 엉뚱한 사람에게 일이
 * 배정되는데, 그건 DB 를 띄워야 검증되는 종류의 버그가 아니다.
 *
 * <h2>판정을 포기하는 것이 정상 동작이다</h2>
 * `speaker_member_id` NULL 은 오류가 아니다(V5.3 주석 · 명세 ANLZ-05). 틀린 이름을 채우면
 * 그 발화가 근거인 액션이 엉뚱한 사람에게 배정되고, 그 사람은 자기 일이 아닌 것을 받는다.
 * **모르는 채로 두는 것이 언제나 더 싸다.**
 */
@Slf4j
@Service
public class SpeakerAttributionResolver {

    /*
     * 시간창 반경. 자막과 정본은 서로 다른 경로로 만들어져 경계가 정확히 맞지 않는다 —
     * 브라우저 음성인식의 구간 분할과 STT 의 구간 분할이 다르기 때문이다.
     *
     * 좁히면 실제 화자의 자막이 창 밖으로 나가 판정 포기가 늘고, 넓히면 옆 발화의 자막이
     * 들어와 오귀속이 늘어난다. 1.5초는 명세와 V5.3 이 정한 값이다.
     */
    private static final int WINDOW_MS = 1_500;

    /*
     * 발화들의 화자를 판정한다.
     *
     * @param utterances 정본 발화. speakerMemberId 가 이미 채워진 것도 그대로 다시 판정한다 —
     *                   재실행은 자막이 더 도착한 뒤일 수 있고, 그때 판정이 달라지는 것이 맞다.
     * @param captions   이 회의의 자막 전체
     * @param attendeeMemberIds 참석자 명단(unknown_person 탈출구 제외). "전원 자막" 판단에 쓴다
     * @return 판정된 것만 담는다. 포기한 발화는 결과에 없다 — 호출자가 그것을 NULL 로 남긴다
     */
    public List<Attribution> resolve(List<Utterance> utterances, List<CaptionChunk> captions,
                                     Set<Long> attendeeMemberIds) {

        List<CaptionChunk> scoped = scopedToAttendees(captions, attendeeMemberIds);

        Set<Long> captioningMembers = captioningMembers(scoped);
        boolean everyAttendeeCaptioned = !attendeeMemberIds.isEmpty()
                && captioningMembers.containsAll(attendeeMemberIds);

        List<Attribution> attributions = new ArrayList<>();
        for (Utterance utterance : utterances) {
            Attribution attribution = resolveOne(utterance, scoped, everyAttendeeCaptioned);
            if (attribution != null) {
                attributions.add(attribution);
            }
        }

        log.info("L1 화자 귀속 — 발화 {}건 중 {}건 판정 (전원자막={})",
                utterances.size(), attributions.size(), everyAttendeeCaptioned);
        return attributions;
    }

    /*
     * 자막 후보를 **참석자 명단 안으로 좁힌다.** 판정에 들어가기 전에 한 번에 거른다.
     *
     * 왜 필요한가 — caption_chunk 의 member_id 가 명단 안이라는 보장이 없다. 회의 도중 나간
     * 사람의 늦게 도착한 자막, 참석 처리 전에 먼저 붙은 자막, 잘못된 세션에 실린 자막이
     * 모두 이 테이블에 들어올 수 있다. 그 값을 그대로 후보로 쓰면 **참석자가 아닌 사람이
     * 화자로 확정되고**, 그 발화를 근거로 만들어진 액션이 그 사람에게 배정된다.
     *
     * 명단 밖 자막이 "전원 자막" 판단에도 섞이면 안 된다. 명단 밖 발신자가 하나 있으면
     * captioningMembers 가 부풀어, 후보 한 명을 확정해도 되는지 판단하는 근거가 흔들린다.
     * 그래서 여기서 한 번 거르고 아래 전부가 좁혀진 목록만 본다.
     *
     * ⚠ 명단이 비어 있으면 모든 자막이 걸러지고 전원 판정 포기가 된다. 그게 맞다 —
     * 참석자를 모르는 채로는 어떤 자막도 "참석자가 말한 것"임을 확인할 수 없다.
     */
    private List<CaptionChunk> scopedToAttendees(List<CaptionChunk> captions, Set<Long> attendeeMemberIds) {
        List<CaptionChunk> scoped = captions.stream()
                .filter(caption -> caption.memberId() != null)
                .filter(caption -> attendeeMemberIds.contains(caption.memberId()))
                .toList();

        if (scoped.size() != captions.size()) {
            // 조용히 버리지 않는다. 명단 밖 자막이 꾸준히 들어온다는 것은 참석자 명단과
            // 자막 전송(CAP-11)이 어긋났다는 신호이고, 그건 판정 품질이 아니라 코드 문제다.
            log.warn("명단 밖 자막을 판정에서 제외한다 — 전체={} 사용={}", captions.size(), scoped.size());
        }
        return scoped;
    }

    private Attribution resolveOne(Utterance utterance, List<CaptionChunk> captions,
                                   boolean everyAttendeeCaptioned) {
        if (utterance.startOffsetMs() == null) {
            // 위치를 모르는 발화는 시간창을 만들 수 없다. 오프셋이 NULL 인 발화가 있다는 것
            // 자체가 적재 쪽 문제이고, 여기서 추측하면 그 문제가 화자 오귀속으로 위장된다.
            return null;
        }

        Map<Long, BigDecimal> loudestByMember = loudestByMember(utterance, captions);

        // host-only 자막이라 창 안 후보는 0명 아니면 1명(host)뿐이다 — 2명 이상 비교할 일이 없다.
        if (loudestByMember.isEmpty()) {
            return null;
        }

        Map.Entry<Long, BigDecimal> onlyCandidate = loudestByMember.entrySet().iterator().next();

        /*
         * 후보가 한 명뿐이라도 바로 확정하면 안 된다 — **자막을 안 켠 참석자가 실제 화자일 수
         * 있다.** 3명 회의에서 host 만 자막을 켜면 모든 구간의 유일한 후보가 host 가 되고, 남의
         * 발화까지 전부 host 것이 된다. 오귀속이 한 건이 아니라 회의 전체 규모로 발생하는 경로다.
         *
         * 전원이 자막을 보낸 회의에서만 "나머지는 그 구간에 말하지 않았다"가 성립한다.
         */
        if (!everyAttendeeCaptioned) {
            return null;
        }
        return new Attribution(utterance.utteranceId(), onlyCandidate.getKey(), SpeakerSource.SELF_STREAM);
    }

    /*
     * 시간창 안에서 참석자별 **최대** rms 를 모은다.
     *
     * 합이 아니라 최대인 이유 — rms 는 "그 구간에서 이 사람 마이크가 얼마나 크게 잡혔나"이고,
     * 자막 조각이 여러 개 걸쳐 있어도 사람이 두 배로 크게 말한 것은 아니다. 합을 쓰면 자막을
     * 잘게 보내는 브라우저가 항상 이긴다 — 판정이 말한 사람이 아니라 청크 분할 방식으로 정해진다.
     */
    /* captions 는 이미 명단 안으로 좁혀진 목록이다(scopedToAttendees). */
    private Map<Long, BigDecimal> loudestByMember(Utterance utterance, List<CaptionChunk> captions) {
        int windowStart = utterance.startOffsetMs() - WINDOW_MS;
        // 종료 오프셋이 없으면 시작 지점만 쓴다. NULL 을 허용하는 컬럼이고(V5.3), 그때 발화를
        // 길이 0 으로 보면 창은 여전히 ±1.5초로 만들어진다.
        int utteranceEnd = utterance.endOffsetMs() != null
                ? utterance.endOffsetMs()
                : utterance.startOffsetMs();
        int windowEnd = utteranceEnd + WINDOW_MS;

        Map<Long, BigDecimal> loudest = new HashMap<>();
        for (CaptionChunk caption : captions) {
            if (caption.memberId() == null || caption.rms() == null || !overlaps(caption, windowStart, windowEnd)) {
                continue;
            }
            loudest.merge(caption.memberId(), caption.rms(),
                    (existing, candidate) -> candidate.compareTo(existing) > 0 ? candidate : existing);
        }
        return loudest;
    }

    /*
     * 자막 구간이 시간창과 겹치는가. 포함이 아니라 **겹침**이다 — 자막 조각이 창보다 길거나
     * 경계에 걸쳐 있을 때 포함으로 보면 실제 화자의 자막을 놓친다.
     */
    private boolean overlaps(CaptionChunk caption, int windowStart, int windowEnd) {
        if (caption.startOffsetMs() == null) {
            return false;
        }
        int captionEnd = caption.endOffsetMs() != null ? caption.endOffsetMs() : caption.startOffsetMs();
        return caption.startOffsetMs() <= windowEnd && captionEnd >= windowStart;
    }

    private Set<Long> captioningMembers(List<CaptionChunk> captions) {
        Set<Long> members = new HashSet<>();
        for (CaptionChunk caption : captions) {
            if (caption.memberId() != null) {
                members.add(caption.memberId());
            }
        }
        return members;
    }

    /*
     * 발화 하나의 판정 결과다.
     *
     * 포기한 발화는 이 레코드를 만들지 않는다 — memberId 가 null 인 Attribution 을 만들면
     * "판정했는데 화자가 없음"과 "판정을 포기함"이 같은 모양이 된다.
     */
    public record Attribution(Long utteranceId, Long speakerMemberId, SpeakerSource speakerSource) {
    }
}
