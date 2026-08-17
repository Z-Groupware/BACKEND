package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.domain.model.AttendeeNameMatcher;
import com.module06.backend.capture.domain.model.CaptionChunk;
import com.module06.backend.capture.domain.model.Utterance;

/*
 * STT 화자 분리 라벨을 **사람에 닻 내린다**(V5.23). 모델이 아니라 산수다.
 *
 * <h2>이 계층이 푸는 문제</h2>
 * Transcribe 는 "이 구간과 저 구간이 같은 목소리다"까지만 답한다 — {@code 3:spk_0} 이 누구인지는
 * 말하지 않는다. 그래서 화자 판정이 둘로 나뉜다.
 *
 *   <b>분리</b>(누가 말을 바꿨나) — 제공자가 한다. 어려운 쪽이고, 우리가 못 하던 쪽이다
 *   <b>식별</b>(그 라벨이 누구냐) — 이 클래스가 한다. 근거가 훨씬 적어도 되는 쪽이다
 *
 * 나누는 것이 핵심이다. 라벨은 회의 내내 일관되므로 <b>한 라벨에 닻 하나만 찾으면 그 사람의
 * 발화 전부가 한꺼번에 풀린다.</b> 회의 후반에 잡힌 근거가 전반부 발화까지 소급해 확정한다.
 *
 * <h2>수동 신호만 쓴다 — 사용자에게 아무것도 시키지 않는다</h2>
 * 회의 시작에 돌아가며 이름을 말하게 하면(롤콜) 닻이 확실해지지만 아무도 그렇게 회의하지
 * 않고, 수동 업로드·온라인 회의 경로에는 붙일 자리도 없다. 그래서 <b>이미 일어난 회의에서
 * 주울 수 있는 것만</b> 쓴다.
 *
 * <ol>
 *   <li>{@link Signal#CAPTION_OVERLAP} — 자막을 보낸 사람의 발화 구간과 겹치는 라벨</li>
 *   <li>{@link Signal#RESPONSE_TO_CALL} — 이름이 불린 직후에 답한 라벨</li>
 *   <li>{@link Signal#ELIMINATION} — 라벨 하나와 사람 하나만 남았을 때</li>
 * </ol>
 *
 * <h2>부분 성공이 이 설계의 목적이다</h2>
 * 예전 rms 판정은 「전원 자막」 게이트 하나로 all-or-nothing 이었다 — 한 명이라도 자막을 안
 * 보내면 회의 전체가 기권이었고, host-only 로 확정된 뒤에는 그게 <b>항상</b> 참이라 모든
 * 발화의 화자가 NULL 이었다.
 *
 * 앵커는 라벨마다 독립이다. 4명 회의에서 2명만 닻이 내려가도 그 2명의 발화는 확정된다.
 * 나머지 라벨의 발화는 NULL 로 남고, 그건 오류가 아니라 <b>아직 근거가 없다</b>는 뜻이다.
 *
 * <h2>기권이 확정보다 싸다는 규칙은 그대로다</h2>
 * 아래 임계값은 전부 "애매하면 닻을 내리지 않는다"는 방향으로 잡혀 있다. 라벨 하나를 잘못
 * 닻 내리면 발화 하나가 아니라 <b>그 사람의 회의 전체가 함께 틀린다</b> — 판정 단위가 커진
 * 만큼 틀렸을 때의 피해도 커졌고, 그래서 예전보다 더 보수적으로 잡았다.
 */
@Slf4j
@Service
public class SpeakerLabelAnchorResolver {

    /*
     * 자막 겹침으로 닻을 내릴 최소 비율.
     *
     * 어떤 사람의 자막이 라벨 A 의 발화 시간 중 절반 이상을 덮으면 A 를 그 사람으로 본다.
     * 1.0 을 요구하지 않는 이유 — 자막은 브라우저 음성인식이라 조용히 말한 구간을 통째로
     * 놓치고, 정본과 구간 분할 방식도 다르다. 절반이면 "이 라벨이 말할 때 그 사람 마이크가
     * 켜져 있었다"가 성립한다.
     */
    static final double COVERAGE_MIN = 0.5;

    /*
     * 그때 2등 라벨이 넘으면 안 되는 비율. **이 값이 이 신호의 안전장치다.**
     *
     * <h2>대면 회의에서는 이 신호를 쓰면 안 된다</h2>
     * host 노트북 마이크 하나로 녹음하는 대면 회의에서는 host 브라우저의 음성인식이 <b>방 안
     * 모두의 목소리</b>를 받아쓴다. 그러면 host 자막이 모든 라벨을 고르게 덮고, 아무 라벨이나
     * host 로 확정된다 — 회의 전체가 host 것이 되는, 예전 「전원 자막」 게이트가 막으려던
     * 실패 그대로다.
     *
     * 온라인 회의(각자 자기 기기)에서는 host 마이크가 host 목소리만 잡으므로 한 라벨만
     * 높고 나머지는 0 에 가깝다. 즉 <b>두 상황이 비율의 모양으로 구분된다</b> — 별도 설정이나
     * 회의 종류 플래그 없이, 신호 자체가 자기가 믿을 만한지를 말한다.
     *
     * 그래서 "1등이 높다"만으로는 부족하고 "2등이 낮다"를 함께 요구한다.
     */
    static final double RUNNER_UP_MAX = 0.25;

    /*
     * 호명-응답으로 닻을 내릴 최소 표 수.
     *
     * 1 표로 내리지 않는다. 이름이 불린 다음 발화가 그 사람이 아닌 경우가 흔하다 — 제3자가
     * 대신 답하거나("걔 오늘 휴가예요"), 부른 사람이 말을 이어가거나, 이름이 호명이 아니라
     * 언급인 경우다("김현지가 지난주에 했었죠"). 한 번은 우연이지만 회의 내내 같은 라벨이
     * 같은 이름 뒤에 반복해서 나오는 것은 우연이 아니다.
     *
     * ⚠ 짧은 회의에서는 이 값을 못 채워 기권이 는다. 그건 의도한 방향이다 — 근거가 두 번도
     * 안 나온 판정으로 그 사람의 회의 전체를 확정하지 않는다.
     */
    static final int MIN_RESPONSE_VOTES = 2;

    /*
     * 그때 1등이 2등보다 몇 배여야 하는가.
     *
     * 라벨 A 뒤에 「김현지」가 3번, 「이태연」이 2번 나왔다면 그건 둘 중 누구인지 모르는
     * 것이지 김현지인 것이 아니다. 배수를 요구하면 그런 표는 기권이 된다.
     */
    static final int RESPONSE_VOTE_MARGIN = 2;

    /*
     * 라벨을 사람에 닻 내린다.
     *
     * @param utterances        회의 발화 전체. **시작 오프셋 순서여야 한다** — 호명-응답 신호가
     *                          "다음 발화"를 목록 순서로 본다
     * @param captions          그 회의의 자막. 참석자 명단 안으로 이미 좁혀진 것이어야 한다
     * @param attendeeMemberIds 참석자 명단(명단 밖 탈출구 제외)
     * @param nameByMemberId    참석자 이름표. 비어 있으면 호명-응답 신호가 통째로 빠진다
     * @return 라벨 → 닻. 닻을 못 내린 라벨은 결과에 없다 — null 을 담은 항목을 만들면
     *         "닻을 내렸는데 사람이 없다"와 "못 내렸다"가 같은 모양이 된다
     */
    public Map<String, Anchor> resolve(List<Utterance> utterances, List<CaptionChunk> captions,
                                       Set<Long> attendeeMemberIds, Map<Long, String> nameByMemberId) {

        List<String> labels = labelsOf(utterances);
        if (labels.isEmpty()) {
            // 화자 분리를 안 쓴 회의다. 로그를 남기지 않는다 — L1 이 라벨 유무를 이미 센다.
            return Map.of();
        }

        Map<String, Long> byCaption = anchorsByCaptionOverlap(utterances, captions);
        Map<String, Long> byResponse = anchorsByResponseToCall(utterances, nameByMemberId);

        Map<String, Anchor> anchors = merge(labels, byCaption, byResponse);
        dropContradictions(anchors, labels.size(), attendeeMemberIds.size());
        anchorByElimination(anchors, labels, attendeeMemberIds);

        log.info("화자 라벨 앵커링 — 라벨 {}개 중 {}개 닻 내림 {} (참석자 {}명)",
                labels.size(), anchors.size(), describe(anchors), attendeeMemberIds.size());
        return anchors;
    }

    /* 발화에 실제로 나타난 라벨. 등장 순서를 지킨다 — 로그가 회의 순서대로 읽힌다. */
    private List<String> labelsOf(List<Utterance> utterances) {
        List<String> labels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Utterance utterance : utterances) {
            if (utterance.speakerLabel() != null && seen.add(utterance.speakerLabel())) {
                labels.add(utterance.speakerLabel());
            }
        }
        return labels;
    }

    // ── 신호 1 · 자막 겹침 ────────────────────────────────────────────────

    /*
     * 자막을 보낸 사람의 마이크가 켜져 있던 구간과 겹치는 라벨을 찾는다.
     *
     * <h2>비율로 본다 — 총량으로 보면 말 많은 사람이 이긴다</h2>
     * 라벨마다 "그 라벨의 발화 시간 중 이 사람 자막이 덮은 비율"을 낸다. 겹친 총 시간으로
     * 비교하면 회의의 80% 를 말한 라벨이 언제나 1등이 되는데, 그건 그 라벨이 그 사람이라는
     * 뜻이 아니라 그냥 말을 많이 했다는 뜻이다.
     *
     * <h2>끝 오프셋이 없는 발화는 분모에서 뺀다</h2>
     * 길이를 모르면 비율을 낼 수 없다. 길이 0 으로 치면 분모만 안 늘고 분자는 늘어 비율이
     * 부풀고, 임의의 길이를 넣으면 그 숫자가 판정을 흔든다.
     */
    private Map<String, Long> anchorsByCaptionOverlap(List<Utterance> utterances,
                                                      List<CaptionChunk> captions) {
        if (captions == null || captions.isEmpty()) {
            return Map.of();
        }

        // 라벨 → 발화 시간 합 · (라벨, 사람) → 자막이 덮은 발화 시간 합
        Map<String, Long> totalMsByLabel = new HashMap<>();
        Map<String, Map<Long, Long>> coveredMsByLabel = new HashMap<>();

        for (Utterance utterance : utterances) {
            String label = utterance.speakerLabel();
            if (label == null || utterance.startOffsetMs() == null || utterance.endOffsetMs() == null) {
                continue;
            }
            long durationMs = utterance.endOffsetMs() - utterance.startOffsetMs();
            if (durationMs <= 0) {
                continue;
            }
            totalMsByLabel.merge(label, durationMs, Long::sum);

            /*
             * 사람마다 이 발화를 **몇 ms 덮었는지** 잰다. "겹치면 발화 전체를 덮은 것으로"
             * 세면 안 된다 — 앞 발화에 걸친 자막 꼬리 1ms 가 20초짜리 발화를 통째로 덮은
             * 것이 되고, 그러면 옆 라벨의 비율이 같이 올라 판별력이 사라진다.
             *
             * 한 발화에 여러 사람의 자막이 걸리면 **모두** 센다. 여기서 하나를 고르면 그
             * 선택이 곧 판정이 되는데, 판정은 아래 임계값이 해야 한다.
             */
            Map<Long, Long> overlapByMember = new HashMap<>();
            for (CaptionChunk caption : captions) {
                if (caption.memberId() == null) {
                    continue;
                }
                long overlapMs = overlapMsOf(caption, utterance);
                if (overlapMs > 0) {
                    overlapByMember.merge(caption.memberId(), overlapMs, Long::sum);
                }
            }
            for (Map.Entry<Long, Long> entry : overlapByMember.entrySet()) {
                // 같은 사람의 자막 조각들이 서로 겹쳐 있으면 합이 발화 길이를 넘는다.
                // 비율이 1 을 넘지 않도록 여기서 가둔다.
                coveredMsByLabel.computeIfAbsent(label, key -> new HashMap<>())
                        .merge(entry.getKey(), Math.min(entry.getValue(), durationMs), Long::sum);
            }
        }

        /*
         * 사람마다 "가장 많이 덮은 라벨"을 고른다. 사람 기준으로 도는 이유 — 자막을 보낸
         * 사람은 보통 host 한 명이고, 그 한 명이 어느 라벨인지를 찾는 것이 이 신호의 전부다.
         */
        Map<Long, Map<String, Double>> coverageByMember = new HashMap<>();
        coveredMsByLabel.forEach((label, byMember) -> {
            long total = totalMsByLabel.getOrDefault(label, 0L);
            if (total <= 0) {
                return;
            }
            byMember.forEach((memberId, covered) -> coverageByMember
                    .computeIfAbsent(memberId, key -> new HashMap<>())
                    .put(label, (double) covered / total));
        });

        Map<String, Long> anchors = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, Double>> entry : coverageByMember.entrySet()) {
            String best = null;
            double bestCoverage = 0;
            double runnerUp = 0;
            for (Map.Entry<String, Double> candidate : entry.getValue().entrySet()) {
                if (candidate.getValue() > bestCoverage) {
                    runnerUp = bestCoverage;
                    bestCoverage = candidate.getValue();
                    best = candidate.getKey();
                } else if (candidate.getValue() > runnerUp) {
                    runnerUp = candidate.getValue();
                }
            }

            if (best == null || bestCoverage < COVERAGE_MIN) {
                continue;
            }
            if (runnerUp > RUNNER_UP_MAX) {
                /*
                 * 자막이 여러 라벨을 고르게 덮었다. **대면 회의의 방 마이크다** — 이 사람의
                 * 자막은 자기 목소리만 담은 것이 아니라 그 자리의 모두를 받아쓴 것이고,
                 * 그러면 어느 라벨이 이 사람인지 이 신호로는 알 수 없다(상수 주석).
                 */
                log.info("자막이 여러 라벨을 고르게 덮어 앵커로 쓰지 않는다 — memberId={} 1등={} {}% 2등={}%",
                        entry.getKey(), best, Math.round(bestCoverage * 100), Math.round(runnerUp * 100));
                continue;
            }
            anchors.put(best, entry.getKey());
        }
        return anchors;
    }

    /*
     * 자막 조각이 발화 구간을 덮은 시간(ms). 안 겹치면 0.
     *
     * ⚠ **경계가 닿기만 한 것은 겹친 것이 아니다.** 발화는 시간축에서 맞붙어 있어(앞 발화의
     * 끝 = 뒷 발화의 시작) 부등호를 하나 잘못 쓰면 모든 자막이 이웃 발화까지 덮은 것으로
     * 잡힌다. 그러면 모든 라벨의 비율이 같이 올라 2등 조건에 걸리고, 판정이 언제나 기권이
     * 된다 — 고치려던 실패가 모양만 바꿔 되돌아온다.
     *
     * 끝 오프셋이 없는 자막은 길이 0 이라 아무것도 덮지 못한다. 그게 맞다 — 언제 끝났는지
     * 모르는 조각으로 "이 구간을 덮었다"고 말할 수 없다.
     */
    private long overlapMsOf(CaptionChunk caption, Utterance utterance) {
        if (caption.startOffsetMs() == null) {
            return 0;
        }
        int captionEnd = caption.endOffsetMs() != null ? caption.endOffsetMs() : caption.startOffsetMs();
        long from = Math.max(caption.startOffsetMs(), utterance.startOffsetMs());
        long to = Math.min(captionEnd, utterance.endOffsetMs());
        return Math.max(0, to - from);
    }

    // ── 신호 2 · 호명 응답 ────────────────────────────────────────────────

    /*
     * 이름이 불린 <b>직후 발화</b>의 라벨을 그 사람으로 본다.
     *
     * <h2>왜 다음 발화인가</h2>
     * 사람은 자기 이름을 잘 부르지 않는다. 그래서 「이태연씨, 이거 해줄 수 있어요?」를 말한
     * 라벨은 이태연이 <b>아닐</b> 가능성이 높고, 그 다음에 답한 라벨이 이태연일 가능성이 높다.
     * 두 사실을 다 쓴다 — 앞은 배제로, 뒤는 표로.
     *
     * <h2>호명인지 언급인지 가리지 않는다</h2>
     * 「~씨」·「~님」 같은 호칭 표를 두면 빠진 항목이 생기는 순간 조용히 기권으로 바뀌고,
     * 직급 호칭까지 넣기 시작하면 회사마다 다른 표를 관리하게 된다. 대신 <b>표를 여러 번
     * 받아야 확정</b>하는 쪽으로 푼다 — 언급은 흩어지고 호명은 같은 라벨에 쌓인다.
     *
     * ⚠ L1.5 주석을 걷어내지 않는다. L1 은 L1.5 <b>앞</b>에 돌기 때문에 아직 주석이 없다.
     * 걷어내는 코드를 넣으면 "여기도 주석이 올 수 있다"는 거짓 정보가 남는다.
     */
    private Map<String, Long> anchorsByResponseToCall(List<Utterance> utterances,
                                                      Map<Long, String> nameByMemberId) {
        if (nameByMemberId == null || nameByMemberId.isEmpty()) {
            return Map.of();
        }

        Map<String, Map<Long, Integer>> votesByLabel = new HashMap<>();
        // 라벨 → 그 라벨이 아닐 사람. 자기 이름을 부른 라벨은 그 사람이 아니라고 본다.
        Map<String, Set<Long>> excludedByLabel = new HashMap<>();

        for (int i = 0; i < utterances.size() - 1; i++) {
            Utterance called = utterances.get(i);
            Utterance answer = utterances.get(i + 1);

            String callerLabel = called.speakerLabel();
            String answerLabel = answer.speakerLabel();
            if (answerLabel == null || answerLabel.equals(callerLabel)) {
                // 같은 라벨이 이어 말한 것이면 응답이 아니다. 자기 이름을 부르고 자기가
                // 답하는 모양이 되어, 그 표는 언제나 자신을 가리킨다.
                continue;
            }

            AttendeeNameMatcher.Match match = AttendeeNameMatcher.find(called.text(), nameByMemberId);
            if (!match.isUnique()) {
                continue;
            }

            votesByLabel.computeIfAbsent(answerLabel, key -> new HashMap<>())
                    .merge(match.memberId(), 1, Integer::sum);
            if (callerLabel != null) {
                excludedByLabel.computeIfAbsent(callerLabel, key -> new HashSet<>())
                        .add(match.memberId());
            }
        }

        Map<String, Long> anchors = new LinkedHashMap<>();
        votesByLabel.forEach((label, votes) -> {
            Long best = null;
            int bestVotes = 0;
            int runnerUp = 0;
            for (Map.Entry<Long, Integer> candidate : votes.entrySet()) {
                if (candidate.getValue() > bestVotes) {
                    runnerUp = bestVotes;
                    bestVotes = candidate.getValue();
                    best = candidate.getKey();
                } else if (candidate.getValue() > runnerUp) {
                    runnerUp = candidate.getValue();
                }
            }

            if (best == null || bestVotes < MIN_RESPONSE_VOTES) {
                return;
            }
            if (bestVotes < runnerUp * RESPONSE_VOTE_MARGIN) {
                log.info("호명 응답 표가 갈려 앵커로 쓰지 않는다 — 라벨={} 1등={}표 2등={}표",
                        label, bestVotes, runnerUp);
                return;
            }
            if (excludedByLabel.getOrDefault(label, Set.of()).contains(best)) {
                /*
                 * 이 라벨이 그 사람의 이름을 부른 적이 있다. 자기 이름을 부르는 일은 드무니
                 * 두 신호가 서로를 부정하는 셈이고, 어느 쪽이 맞는지 정할 근거가 없다.
                 */
                log.info("이름을 부른 적 있는 라벨이라 앵커로 쓰지 않는다 — 라벨={} memberId={}", label, best);
                return;
            }
            anchors.put(label, best);
        });
        return anchors;
    }

    // ── 합치기 · 모순 제거 · 소거법 ───────────────────────────────────────

    /*
     * 두 신호를 합친다. **어긋나면 둘 다 버린다.**
     *
     * 한쪽을 우선하지 않는 이유 — 두 신호는 서로 독립적인 근거라 어느 쪽이 더 믿을 만한지
     * 정할 방법이 없다. 어긋났다는 것은 둘 중 하나가 틀렸다는 뜻이고, 어느 쪽인지 모르는
     * 채로 하나를 고르면 그게 곧 오귀속이다.
     */
    private Map<String, Anchor> merge(List<String> labels, Map<String, Long> byCaption,
                                      Map<String, Long> byResponse) {
        Map<String, Anchor> anchors = new LinkedHashMap<>();
        for (String label : labels) {
            Long caption = byCaption.get(label);
            Long response = byResponse.get(label);

            if (caption != null && response != null) {
                if (!caption.equals(response)) {
                    log.warn("두 신호가 다른 사람을 가리켜 라벨을 비운다 — 라벨={} 자막겹침={} 호명응답={}",
                            label, caption, response);
                    continue;
                }
                anchors.put(label, new Anchor(caption, Signal.CAPTION_OVERLAP));
            } else if (caption != null) {
                anchors.put(label, new Anchor(caption, Signal.CAPTION_OVERLAP));
            } else if (response != null) {
                anchors.put(label, new Anchor(response, Signal.RESPONSE_TO_CALL));
            }
        }
        return anchors;
    }

    /*
     * 한 사람이 여러 라벨에 닻을 내린 경우를 정리한다.
     *
     * <h2>같은 사람이 여러 라벨인 것은 정상일 수도, 오판일 수도 있다</h2>
     * 화자 분리는 같은 사람을 여러 라벨로 쪼개는 쪽으로 틀린다(마이크 거리가 바뀌거나
     * 목소리 크기가 달라지면 그렇다). 그때는 두 라벨이 같은 사람인 것이 <b>맞다.</b>
     *
     * 그런데 라벨 수가 참석자 수 이하인데 같은 사람이 둘이면 이야기가 다르다 — 쪼개질 이유가
     * 없는데 겹쳤으므로 둘 중 하나가 틀린 것이고, 어느 쪽인지 모른다. 그때만 버린다.
     *
     * 즉 <b>라벨이 넘칠 때만 중복을 허용한다.</b> 이 구분이 없으면 둘 중 하나를 반드시
     * 잃는다 — 전부 허용하면 오판이 살아남고, 전부 버리면 정상적인 과분할이 기권이 된다.
     */
    private void dropContradictions(Map<String, Anchor> anchors, int labelCount, int attendeeCount) {
        if (labelCount > attendeeCount) {
            return;
        }

        Map<Long, List<String>> labelsByMember = new HashMap<>();
        anchors.forEach((label, anchor) -> labelsByMember
                .computeIfAbsent(anchor.memberId(), key -> new ArrayList<>()).add(label));

        labelsByMember.forEach((memberId, labels) -> {
            if (labels.size() <= 1) {
                return;
            }
            log.warn("한 사람이 라벨 여러 개에 닻을 내려 전부 비운다 — memberId={} 라벨={} (라벨 {}개 · 참석자 {}명)",
                    memberId, labels, labelCount, attendeeCount);
            labels.forEach(anchors::remove);
        });
    }

    /*
     * 마지막 하나를 소거법으로 채운다.
     *
     * <h2>라벨 수와 참석자 수가 같을 때만 한다</h2>
     * 그래야 "남은 라벨 = 남은 사람"이 성립한다. 라벨이 더 많으면 남은 라벨 둘이 같은 사람일
     * 수 있고, 더 적으면 남은 라벨 하나에 두 사람이 섞여 있을 수 있다 — 두 경우 모두 남은
     * 사람을 그 라벨에 넣으면 남의 발화가 그 사람 것이 된다.
     *
     * V5.3 의 ELIMINATION 과 같은 판단이다(참석자 2명일 때만 소거법을 썼다). 여기서는 그
     * 조건이 "라벨과 사람이 일대일로 남았는가"로 바뀐 것뿐이다.
     */
    private void anchorByElimination(Map<String, Anchor> anchors, List<String> labels,
                                     Set<Long> attendeeMemberIds) {
        if (labels.size() != attendeeMemberIds.size()) {
            return;
        }

        List<String> unanchored = labels.stream().filter(label -> !anchors.containsKey(label)).toList();
        if (unanchored.size() != 1) {
            return;
        }

        Set<Long> taken = new HashSet<>();
        anchors.values().forEach(anchor -> taken.add(anchor.memberId()));
        List<Long> remaining = attendeeMemberIds.stream().filter(id -> !taken.contains(id)).toList();
        if (remaining.size() != 1) {
            return;
        }

        anchors.put(unanchored.get(0), new Anchor(remaining.get(0), Signal.ELIMINATION));
        log.info("소거법으로 마지막 라벨에 닻을 내렸다 — 라벨={} memberId={}",
                unanchored.get(0), remaining.get(0));
    }

    /*
     * 로그에 실을 닻 내역.
     *
     * <b>이 문자열이 이 계층의 계측 전부다.</b> 예전 L1 은 「발화 76건 중 0건 판정
     * (전원자막=false)」만 남겼는데, 그 로그로는 "가끔 기권"과 "구조적으로 항상 기권"이 같은
     * 모양이라 1년 내내 고장난 채로 정상처럼 보였다. 라벨마다 닻이 있는지 · 무슨 신호였는지가
     * 보여야 같은 실패가 반복되지 않는다.
     */
    private String describe(Map<String, Anchor> anchors) {
        if (anchors.isEmpty()) {
            return "[]";
        }
        StringBuilder text = new StringBuilder("[");
        anchors.forEach((label, anchor) -> text.append(label).append("=memberId:").append(anchor.memberId())
                .append('(').append(anchor.signal()).append(") "));
        return text.toString().stripTrailing() + "]";
    }

    /*
     * 라벨 하나에 내린 닻.
     *
     * signal 을 함께 담는 이유는 SpeakerSource 가 근거를 값으로 남기는 것과 같다 — 오귀속이
     * 발견됐을 때 어느 신호를 조일지 알아야 한다. 다만 이 값은 DB 로 가지 않는다. 저장되는
     * 근거는 {@code DIARIZATION_ANCHORED} 하나이고(라벨 하나에 여러 신호가 함께 표를 던지는
     * 것이 정상이라 하나로 정해지지 않는다), 신호별 내역은 로그에만 남는다.
     */
    public record Anchor(Long memberId, Signal signal) {
    }

    /* 닻을 내린 근거. */
    public enum Signal {

        /* 자막을 보낸 사람의 발화 구간이 이 라벨을 지배적으로 덮었다. */
        CAPTION_OVERLAP,

        /* 이 사람의 이름이 불린 직후 이 라벨이 답한 일이 반복됐다. */
        RESPONSE_TO_CALL,

        /* 라벨 하나와 사람 하나만 남아 나머지로 정해졌다. */
        ELIMINATION
    }
}
