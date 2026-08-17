package com.module06.backend.capture.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.domain.model.CaptionChunk;
import com.module06.backend.capture.domain.model.SpeakerSource;
import com.module06.backend.capture.domain.model.Utterance;

/*
 * L1 화자 귀속의 판정 로직이다. **모델이 아니라 산수다**(명세 「L1 화자 귀속 (rms·명단으로 코드 판정)」).
 *
 * <h2>2026-08-17 · 주 경로가 화자 분리 라벨로 바뀌었다</h2>
 * 이 클래스는 원래 rms 하나로 판정했다. 그 설계는 **구조적으로 항상 기권했다** —
 *
 *   1. 녹음이 host 한 대로 확정되면서 자막도 host 만 보낸다(CaptionController host-only)
 *   2. 그래서 {@code everyAttendeeCaptioned} 게이트가 참석자 2명 이상인 모든 회의에서 거짓이다
 *   3. 즉 **모든 발화의 화자가 NULL 이었다.** 자막을 아무리 쌓아도 바뀌지 않는다
 *
 * 게이트를 완화하는 방향은 막혀 있다(3번 분기 주석). 그래서 STT 화자 분리를 켜고(V5.23),
 * 라벨을 사람에 닻 내리는 판정을 앞에 두었다(SpeakerLabelAnchorResolver).
 *
 * <h2>두 경로는 라벨의 유무로 배타적이다</h2>
 * 발화에 라벨이 있으면 <b>라벨 경로만</b> 보고, 없으면 <b>rms 경로만</b> 본다. 한 발화를 두고
 * 두 판정이 다투는 일이 없다 — 그것이 예전에 화자 분리를 켜지 않기로 했던 이유였고
 * (SttTranscribeJobAdapter 주석), 그 지적은 지금도 옳다. 둘이 다를 때 어느 쪽이 맞는지
 * 판단할 근거가 없으므로 애초에 다툴 자리를 만들지 않는다.
 *
 * rms 경로를 지우지 않은 이유 — 라벨이 없는 발화가 계속 들어온다. 스텁 프로파일, whisper
 * 재처리, V5.23 이전에 적재된 블록이 그렇다. 그 발화들에는 여전히 예전 판정이 유일한 경로다.
 *
 * <h2>rms 경로가 하는 일(라벨 없는 발화)</h2>
 * 정본(transcript_chunk)과 자막(caption_chunk)은 성격이 정반대다.
 *   정본 = 녹음을 STT 로 받아쓴 것 — 정확하지만 **화자를 모른다**
 *   자막 = 참석자 브라우저가 보낸 것 — 부정확하지만 **화자는 확실하다**
 * 둘을 ±1.5초 시간창에서 겹쳐 보고, 그 구간 마이크 음량(rms)이 가장 큰 참석자를 화자로 정한다.
 * 다중 후보 비교(1·2등 dB 차이)와 소거법 분기는 host-only 전환 때 걷어냈다 — 시간창 안 후보가
 * 항상 0명 아니면 1명(host)이라 성립할 표본 자체가 없어졌다.
 *
 * <h2>왜 이 클래스가 저장소·HTTP 의존이 없나</h2>
 * 판정이 순수 계산이라 저장소도 HTTP 도 필요 없다(앵커 resolver 도 같다). 그래서 경계 입력
 * (자막 미사용 참석자, 후보 0건, 닻 없는 라벨)을 전부 단위 테스트로 고정할 수 있다 —
 * 이 판정이 틀리면 엉뚱한 사람에게 일이 배정되는데, 그건 DB 를 띄워야 검증되는 종류의
 * 버그가 아니다.
 *
 * <h2>판정을 포기하는 것이 정상 동작이다</h2>
 * `speaker_member_id` NULL 은 오류가 아니다(V5.3 주석 · 명세 ANLZ-05). 틀린 이름을 채우면
 * 그 발화가 근거인 액션이 엉뚱한 사람에게 배정되고, 그 사람은 자기 일이 아닌 것을 받는다.
 * **모르는 채로 두는 것이 언제나 더 싸다.**
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakerAttributionResolver {

    /* 라벨을 사람에 닻 내리는 판정. 이것도 순수 계산이라 단위 테스트에서 그대로 만들어 쓴다. */
    private final SpeakerLabelAnchorResolver anchorResolver;

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
     *                   **시작 오프셋 순서여야 한다**(앵커의 호명-응답 신호가 순서를 본다)
     * @param captions   이 회의의 자막 전체
     * @param attendeeMemberIds 참석자 명단(unknown_person 탈출구 제외). "전원 자막" 판단과
     *                   소거법 앵커에 쓴다
     * @param nameByMemberId 참석자 이름표. 호명-응답 앵커에 쓴다 — 비어 있으면 그 신호만 빠지고
     *                   나머지 판정은 그대로 돈다
     * @return 판정된 것만 담는다. 포기한 발화는 결과에 없다 — 호출자가 그것을 NULL 로 남긴다
     */
    public List<Attribution> resolve(List<Utterance> utterances, List<CaptionChunk> captions,
                                     Set<Long> attendeeMemberIds, Map<Long, String> nameByMemberId) {

        List<CaptionChunk> scoped = scopedToAttendees(captions, attendeeMemberIds);

        Set<Long> captioningMembers = captioningMembers(scoped);
        boolean everyAttendeeCaptioned = !attendeeMemberIds.isEmpty()
                && captioningMembers.containsAll(attendeeMemberIds);

        Map<String, SpeakerLabelAnchorResolver.Anchor> anchors =
                anchorResolver.resolve(utterances, scoped, attendeeMemberIds, nameByMemberId);

        List<Attribution> attributions = new ArrayList<>();
        int labelled = 0;
        /* 라벨이 없어 rms 경로여야 했는데, 전원 자막이 아니라 아예 돌지 못한 발화 수. */
        int rmsBlocked = 0;
        for (Utterance utterance : utterances) {
            Attribution attribution;
            if (utterance.speakerLabel() != null) {
                labelled++;
                attribution = resolveByLabel(utterance, anchors);
            } else if (everyAttendeeCaptioned) {
                attribution = resolveByRms(utterance, scoped);
            } else {
                /*
                 * 전원이 자막을 보낸 회의에서만 "나머지는 그 구간에 말하지 않았다"가 성립한다.
                 * 그게 아니면 창 안 유일한 후보를 확정할 수 없다 — 자막을 안 켠 참석자가 실제
                 * 화자일 수 있고, 그러면 **남의 발화까지 전부 그 한 명 것이 된다.**
                 *
                 * ⚠ 이 게이트를 메서드 앞으로 끌어올리면 안 된다. 예전에는 resolve 초입에서
                 * 조기 반환했는데, 그러면 **라벨 있는 발화까지 함께 막힌다** — 라벨 경로는
                 * 자막과 무관하게 성립하므로 전원 자막이 아니어도 판정할 수 있어야 한다.
                 * 게이트가 지켜야 할 것은 rms 판정 하나뿐이다.
                 */
                rmsBlocked++;
                attribution = null;
            }
            if (attribution != null) {
                attributions.add(attribution);
            }
        }

        if (rmsBlocked > 0) {
            warnCannotAttribute(rmsBlocked, attendeeMemberIds, captioningMembers);
        }

        /*
         * **라벨 있는 발화 수를 함께 남긴다.** 판정 건수만 남기면 「0건 판정」이 서로 다른
         * 사고를 같은 모양으로 덮는다 — 화자 분리가 안 켜졌거나(라벨 0), 켜졌는데 닻이 하나도
         * 안 붙었거나(라벨 있음 · 앵커 0), 라벨도 자막도 없거나. 셋은 고칠 곳이 전부 다르다.
         *
         * 앵커 내역은 앵커 resolver 가 자기 로그로, rms 경로가 막힌 사유는 위 WARN 이 남긴다.
         */
        log.info("L1 화자 귀속 — 발화 {}건 중 {}건 판정 (라벨 있는 발화={}건 · 닻 내린 라벨={}개 · 전원자막={})",
                utterances.size(), attributions.size(), labelled, anchors.size(), everyAttendeeCaptioned);
        return attributions;
    }

    /*
     * 라벨 경로. 그 라벨에 닻이 내려 있으면 확정하고, 없으면 기권한다.
     *
     * 기권이 이 경로의 정상 동작이다 — **라벨이 있다는 것은 "구분은 됐다"이지 "누구인지 안다"가
     * 아니다.** 닻 없는 라벨의 발화를 아무 참석자에게나 붙이면 그 사람의 회의 전체가 남의
     * 말로 채워진다. 판정 단위가 발화가 아니라 라벨이라 틀렸을 때의 피해도 그만큼 크다.
     */
    private Attribution resolveByLabel(Utterance utterance,
                                       Map<String, SpeakerLabelAnchorResolver.Anchor> anchors) {
        SpeakerLabelAnchorResolver.Anchor anchor = anchors.get(utterance.speakerLabel());
        if (anchor == null) {
            return null;
        }
        return new Attribution(utterance.utteranceId(), anchor.memberId(),
                SpeakerSource.DIARIZATION_ANCHORED);
    }

    /*
     * rms 경로가 왜 한 건도 못 도는지를 갈라서 남긴다. 셋은 원인이 다르고 손댈 곳도 다르다.
     *
     *   참석자 명단 없음  적재·조회 문제다. 참석자를 모르면 어떤 자막도 "참석자가 말한 것"임을
     *                     확인할 수 없다
     *   자막 0건          수동 업로드(CAP-10) 경로이거나 자막 전송이 안 붙은 것이다
     *   일부만 자막       **host-only 자막 정책에서 다인원 회의의 영구 상태다.** 이번 회의만의
     *                     문제가 아니라 설계에서 오는 것이므로 회의를 다시 해도 같다
     *
     * <h2>⚠ 이 WARN 의 뜻이 좁아졌다(V5.23)</h2>
     * 예전에는 "이 회의의 발화 전부가 화자 미정"이라는 뜻이었다. 지금은 **라벨이 없는 발화에
     * 한한다** — 라벨이 붙은 발화는 이 게이트와 무관하게 앵커로 판정된다. 그래서 건수도 전체가
     * 아니라 실제로 막힌 수만 받는다.
     *
     * 그리고 이제 이 상태에는 **손댈 곳이 있다.** 예전에는 "설계에서 오는 영구 상태"가 결론
     * 이었지만, 지금은 그 회의에 화자 분리가 안 켜졌다는 뜻이기도 하다(스텁 · whisper ·
     * V5.23 이전 블록). 그래서 메시지가 그쪽을 가리킨다 — 자막을 더 모으라고 하면 안 된다.
     */
    private void warnCannotAttribute(int blockedCount, Set<Long> attendeeMemberIds,
                                     Set<Long> captioningMembers) {
        if (attendeeMemberIds.isEmpty()) {
            log.warn("L1 rms 경로 — 참석자 명단이 비어 판정하지 않는다. 라벨 없는 발화 {}건 화자 미정",
                    blockedCount);
            return;
        }
        if (captioningMembers.isEmpty()) {
            log.warn("L1 rms 경로 — 이 회의의 자막이 0건이라 판정하지 않는다. 라벨 없는 발화 {}건 화자 미정",
                    blockedCount);
            return;
        }
        log.warn("L1 rms 경로 — 참석자 {}명 중 {}명만 자막을 보내 전원 자막이 성립하지 않는다."
                        + " 라벨 없는 발화 {}건이 화자 미정으로 남고, **자막이 더 쌓여도 이 조건에서는"
                        + " 달라지지 않는다** (자막은 host 만 보낸다 — CaptionController host 전용)."
                        + " 이 발화들에 화자 분리 라벨이 없다는 것이 실제 원인이다 — STT 제출에"
                        + " ShowSpeakerLabels 가 빠졌거나 V5.23 이전에 적재된 블록인지 볼 것.",
                attendeeMemberIds.size(), captioningMembers.size(), blockedCount);
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

    /*
     * rms 경로 — **라벨이 없는 발화에만** 쓴다(클래스 주석: 두 경로는 배타적이다).
     *
     * 화자 분리를 켠 뒤로 이 경로를 타는 것은 스텁 프로파일 · whisper 재처리 · V5.23 이전에
     * 적재된 블록뿐이다. 그 발화들에서는 예전과 똑같이 판정한다.
     *
     * ⚠ 전원 자막 검사는 여기 없다 — 호출자(resolve)가 그 전에 걸러 한 건도 넘기지 않는다.
     * 두 곳에서 같은 조건을 보면 한쪽만 고쳐졌을 때 "왜 판정이 안 나오지"가 두 배로 어려워진다.
     */
    private Attribution resolveByRms(Utterance utterance, List<CaptionChunk> captions) {
        if (utterance.startOffsetMs() == null) {
            // 위치를 모르는 발화는 시간창을 만들 수 없다. 오프셋이 NULL 인 발화가 있다는 것
            // 자체가 적재 쪽 문제이고, 여기서 추측하면 그 문제가 화자 오귀속으로 위장된다.
            return null;
        }

        Map<Long, BigDecimal> loudestByMember = loudestByMember(utterance, captions);

        /*
         * host-only 전환 이후에는 창 안 후보가 0명 아니면 1명(host)이어야 정상이다. 하지만
         * 전환 이전에 저장된 참석자 자막이 아직 남아 있거나, 명단·전송 경로에 다른 문제가
         * 생기면 2명 이상이 잡힐 수 있다 — 그때 HashMap 반복 순서로 하나를 골라 확정하면
         * 안 된다. 그건 코드가 화자를 정한 게 아니라 반복 순서가 정한 것이다.
         */
        if (loudestByMember.size() != 1) {
            return null;
        }

        Map.Entry<Long, BigDecimal> onlyCandidate = loudestByMember.entrySet().iterator().next();
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
