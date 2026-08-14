package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.Utterance;

/*
 * L4 가 담당자를 비워 둔 tuple 을, 근거 발화 주변에서 **글자 하나 차이인 참석자**와 잇는다.
 * **모델이 아니라 코드가 판정한다** — L6·L7 과 같은 성격의 계층이다.
 *
 * <h2>왜 필요한가 (2026-08-14 실측)</h2>
 * STT 가 이름을 한 글자 잘못 듣는다(김현지 → 김현진). 그러면 L4 는 "명시적 호명이다"까지는
 * 알아보고도(assigneeSource=EXPLICIT_CALL) 그 이름을 참석자 명단에 못 이어 담당자를 비운다.
 * 이름이 틀린 조건에서 담당자 정확도가 **0%** 였고, 이름만 손으로 고치면 명시적 호명을 2/2 로
 * 맞혔다. 즉 모델의 판단력 문제가 아니라 이름 표기 문제다.
 *
 * <h2>왜 프롬프트를 고치지 않는가</h2>
 * 네 갈래를 실측으로 시도해 셋이 실패했다 — 커스텀 어휘(전사가 바뀌지 않음) · L1.5 지시어
 * 해소("무엇"은 풀지만 "누가"는 풀지 않음) · 근거 후보 확대(주제 전체를 후보로 줘도 0%).
 * 남은 갈래가 이것이고, 프롬프트를 "비슷하면 추측하라"로 완화하는 방향은 **하면 안 된다** —
 * 같은 실측에서 20 회 넘는 판정 중 오답이 0 건이었고, 그 0 이 지금의 규칙 덕분이다.
 * 엉뚱한 사람에게 일이 배정되는 것은 담당자가 비는 것보다 나쁘다.
 * 그래서 이 판단을 모델에게 맡기지 않고 코드가 결정론으로, 조건을 좁혀서 한다.
 *
 * <h2>세 가지 조건을 모두 만족할 때만 잇는다</h2>
 * <ol>
 *   <li>담당자가 비어 있다 — 모델이 정한 담당자를 코드가 덮어쓰지 않는다.</li>
 *   <li>{@code assigneeSource == EXPLICIT_CALL} — 발화에 이름이 불린 건만 본다.
 *       1인칭(FIRST_PERSON)은 화자가 담당자라는 뜻이라 이름을 찾을 일이 아니다
 *       ({@code speakerMemberId} 가 필요하고, 그건 자막이 있어야 채워진다).
 *       source 가 null(판정 불가)인 것도 보지 않는다 — 호명인지조차 모르는 발화에서
 *       이름을 주우면 그건 근거 없는 배정이다.</li>
 *   <li>거리 최소인 참석자가 **정확히 한 명** — 둘 이상이면 기권한다. 참석자가 많은 회의에서
 *       비슷한 이름이 겹칠 수 있고, 그때 하나를 고르면 오답 0 건이라는 성질이 깨진다.</li>
 * </ol>
 *
 * <h2>이은 tuple 은 자동확정되지 않는다</h2>
 * {@code assignee_near_matched} 로 남고 L7 이 그 행을 자동확정에서 뺀다. 표본이 회의 1 건
 * 규모라 "오답 0 건"을 자동확정의 근거로 쓸 수 없고, 사람이 확인하는 쪽이 게이트를 조이는
 * 방향이다. 담당자를 채우는 값은 검토 화면의 **제안**이지 확정이 아니다.
 */
@Slf4j
@Service
public class NearNameAssigneeResolver {

    /*
     * 허용 편집거리. **한 글자까지다.**
     *
     * 두 글자까지 넓히면 실측에서 6 건을 더 잡을 수 있었지만 그 조건의 오답률은 재지 않았다.
     * 넓히는 변경은 여기 하나를 고치면 되고, 그때는 실제 회의 정답 데이터로 오답을 먼저 재야
     * 한다 — 지금 값은 "오답 0 건"이 확인된 유일한 값이다.
     */
    static final int MAX_DISTANCE = 1;

    /*
     * 근거 발화 기준 앞뒤 몇 발화까지 이름을 찾을지. L5 의 좁은 시야(_evidence_window)와 같은
     * 폭이다 — 같은 회의를 보는 두 계층이 서로 다른 폭을 쓰면 한쪽이 찾은 근거를 다른 쪽이
     * 못 보는 상태가 생긴다.
     *
     * 이보다 넓히지 않는다. 실측에서 남은 기권 12 건은 담당자가 훨씬 앞 발화에서 지명된
     * 경우였는데, 그 거리까지 넓히면 회의 내내 이름이 불린 사람이 아무 배정에나 붙는다.
     */
    static final int EVIDENCE_WINDOW = 3;

    /*
     * L1.5 주석을 걷어낸다. AnalysisOrchestrator.annotate() 가 원문 뒤에 붙이는 형식이다.
     *
     * 걷어내는 이유 — 주석에는 참석자 이름이 **정확한 표기로** 들어간다. 그걸 그대로 두고
     * 찾으면 "전사에 불린 이름"이 아니라 "L1.5 가 적어 준 이름"에 반응하게 되고, 두 계층의
     * 효과가 한 값에 섞여 어느 쪽이 담당자를 이었는지 되짚을 수 없다. L1.5 는 실측에서
     * 담당자 판정을 바꾸지 못한 계층이고, 그 결론을 이 코드가 흐리게 만들지 않는다.
     */
    private static final Pattern L1_5_ANNOTATION =
            Pattern.compile("\\s*\\[지시어 \"[^\"]*\" → [^\\]]*\\]");

    /*
     * 담당자가 빈 tuple 을 참석자와 이어 본다.
     *
     * @param tuples          L4 가 그 주제에서 뽑은 tuple 전부. 순서를 그대로 돌려준다 —
     *                        호출자가 근거 발화로 확정 항목을 되짚으므로 순서가 곧 대응이다
     * @param utterances      그 주제의 발화. **L4 에 실어 보낸 것과 같은 목록이어야 한다** —
     *                        모델이 못 본 발화에서 이름을 주우면 근거 발화와 담당자가 어긋난다
     * @param nameByMemberId  참석자 memberId → 이름. 명단 밖 탈출구(personId=null)는 빠진 것
     * @return tuple 마다 판정 결과. 잇지 못한 것은 원래 tuple 을 그대로 담아 돌려준다
     */
    public List<Resolved> resolve(List<AssignmentTuple> tuples, List<Utterance> utterances,
                                  Map<Long, String> nameByMemberId) {
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<Resolved> resolved = new ArrayList<>(tuples.size());
        int linked = 0;
        for (AssignmentTuple tuple : tuples) {
            Long memberId = nearMatch(tuple, utterances, nameByMemberId);
            if (memberId == null) {
                resolved.add(new Resolved(tuple, false));
                continue;
            }
            /*
             * assigneeSource 를 그대로 둔다. 그 값은 "모델이 담당자를 그렇게 정한 근거"이고
             * 여기서 바뀐 것은 담당자를 **명단에 이었다**는 사실뿐이다. 새 source 값을 만들면
             * 전송 계약(Python 이 보내는 값)에 없는 값이 도메인에 생긴다 — 이었다는 사실은
             * assignee_near_matched 가 따로 남긴다(AssigneeSource 주석).
             */
            resolved.add(new Resolved(new AssignmentTuple(tuple.title(), memberId,
                    tuple.assigneeSource(), tuple.dueDate(), tuple.evidenceUtteranceId()), true));
            linked++;
        }

        if (linked > 0) {
            log.info("근접 매칭으로 담당자를 이었다 — tuple {}건 중 {}건 (편집거리 {} 이내 · 후보 유일)",
                    tuples.size(), linked, MAX_DISTANCE);
        }
        return resolved;
    }

    /* 이을 참석자의 memberId. 조건을 하나라도 못 채우면 null(기권)이다. */
    private Long nearMatch(AssignmentTuple tuple, List<Utterance> utterances,
                           Map<Long, String> nameByMemberId) {
        if (tuple.assigneeCandidateMemberId() != null) {
            return null;
        }
        if (tuple.assigneeSource() != AssigneeSource.EXPLICIT_CALL) {
            return null;
        }
        if (tuple.evidenceUtteranceId() == null || nameByMemberId == null || nameByMemberId.isEmpty()) {
            return null;
        }

        String window = windowText(utterances, tuple.evidenceUtteranceId());
        if (window.isBlank()) {
            return null;
        }

        Long best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tied = false;
        for (Map.Entry<Long, String> entry : nameByMemberId.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            String name = entry.getValue().trim();
            int distance = minDistance(window, name);
            if (distance > allowedDistance(name)) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
                tied = false;
            } else if (distance == bestDistance) {
                tied = true;
            }
        }

        if (best == null) {
            return null;
        }
        if (tied) {
            /*
             * 같은 거리의 참석자가 둘 이상이다. 하나를 고르면 그 배정은 검증할 수 없는
             * 추측이 된다 — 기권하면 사람이 검토 화면에서 지정한다(PR #495).
             */
            log.info("근접 후보가 둘 이상이라 담당자를 잇지 않는다 — 근거 발화={} 거리={}",
                    tuple.evidenceUtteranceId(), bestDistance);
            return null;
        }
        return best;
    }

    /*
     * 이름 길이에 따라 허용 거리를 줄인다.
     *
     * 두 글자 이름에 한 글자 차이를 허용하면 절반이 달라도 같은 이름으로 보는 것이고, 그
     * 폭이면 무관한 낱말도 걸린다. 세 글자(한국어 성명의 보통 길이) 이상에서만 한 글자를
     * 허용하고, 그보다 짧으면 정확히 같을 때만 잇는다.
     */
    private int allowedDistance(String name) {
        return name.length() >= 3 ? MAX_DISTANCE : 0;
    }

    /*
     * 근거 발화 ±{@value #EVIDENCE_WINDOW} 발화의 원문을 이어 붙인다.
     *
     * 근거 발화가 목록에 없으면 빈 문자열이다 — 그 경우는 L4 에 실어 보낸 발화와 여기 넘어온
     * 발화가 다르다는 뜻이므로, 엉뚱한 자리에서 이름을 줍기보다 기권하는 편이 낫다
     * (annotate() 가 원문 확인에 실패하면 주석을 안 붙이는 것과 같은 판단이다).
     */
    private String windowText(List<Utterance> utterances, long evidenceUtteranceId) {
        if (utterances == null || utterances.isEmpty()) {
            return "";
        }

        int at = -1;
        for (int i = 0; i < utterances.size(); i++) {
            Long id = utterances.get(i).utteranceId();
            if (id != null && id == evidenceUtteranceId) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            log.debug("근거 발화가 주제 발화 목록에 없어 근접 매칭을 건너뛴다 — 근거 발화={}",
                    evidenceUtteranceId);
            return "";
        }

        int from = Math.max(0, at - EVIDENCE_WINDOW);
        int to = Math.min(utterances.size() - 1, at + EVIDENCE_WINDOW);

        StringBuilder window = new StringBuilder();
        for (int i = from; i <= to; i++) {
            String text = utterances.get(i).text();
            if (text == null || text.isBlank()) {
                continue;
            }
            window.append(L1_5_ANNOTATION.matcher(text).replaceAll("")).append(' ');
        }
        return window.toString();
    }

    /*
     * {@code haystack} 안에서 {@code name} 과 가장 가까운 조각의 편집거리.
     *
     * 형태소 분석이나 조사 목록을 두지 않는다. 이름 길이 ±1 폭의 조각을 전부 훑으면
     * "김현진님이"의 "김현진"이 그대로 잡히고, 조사·호칭 표를 관리할 필요가 없다 —
     * 그 표는 빠진 항목이 생기는 순간 조용히 기권으로 바뀌는 종류의 코드다.
     */
    private int minDistance(String haystack, String name) {
        int n = name.length();
        int best = Integer.MAX_VALUE;
        for (int length = Math.max(1, n - 1); length <= n + 1; length++) {
            for (int start = 0; start + length <= haystack.length(); start++) {
                int distance = levenshtein(haystack.substring(start, start + length), name);
                if (distance == 0) {
                    return 0;
                }
                best = Math.min(best, distance);
            }
        }
        return best;
    }

    /* 두 행만 들고 도는 표준 편집거리. 비교 대상이 이름 길이 ±1 이라 길이가 늘 짧다. */
    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1]
                        + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    /*
     * 판정 결과 하나.
     *
     * nearMatched 를 tuple 안에 넣지 않고 밖에 둔다 — AssignmentTuple 은 **L4 가 뽑은 것**을
     * 담는 계약이고, 코드가 이었다는 사실은 그 계약의 일부가 아니다. 저장 행(TupleRow)에서
     * 합쳐진다.
     */
    public record Resolved(AssignmentTuple tuple, boolean nearMatched) {
    }
}
