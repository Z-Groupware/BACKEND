package com.module06.backend.capture.domain.model;

import java.util.Map;

/*
 * 글 안에서 **참석자 이름**을 찾는다. STT 가 한 글자 잘못 들어도 잇는다.
 *
 * <h2>왜 도메인에 있나 — 두 계층이 같은 판정을 한다</h2>
 * 원래는 NearNameAssigneeResolver 안의 private 메서드였다. L1 의 화자 라벨 앵커링
 * (SpeakerLabelAnchorResolver)이 같은 판정을 필요로 하면서 밖으로 꺼냈다 — "누가 불렸나"를
 * 두 곳이 서로 다른 규칙으로 판정하면, 담당자를 이은 근거와 화자를 정한 근거가 같은 발화를
 * 두고 갈린다. 그때 어느 쪽이 맞는지 판단할 방법이 없다.
 *
 * <h2>2026-08-14 실측이 정한 규칙이다</h2>
 * STT 가 이름을 한 글자 잘못 듣는다(김현지 → 김현진). 그 조건에서 담당자 정확도가 **0%**
 * 였고, 이름만 손으로 고치면 명시적 호명을 2/2 로 맞혔다. 즉 모델의 판단력이 아니라 이름
 * 표기 문제였다. 같은 실측에서 이 규칙의 오답이 20 회 넘는 판정 중 **0 건**이었고, 그 0 은
 * 아래 두 제약이 만든 것이다 — 넓히려면 실제 회의 정답 데이터로 오답을 먼저 재야 한다.
 *
 * <h2>형태소 분석이나 조사 목록을 두지 않는다</h2>
 * 이름 길이 ±1 폭의 조각을 전부 훑으면 "김현진님이"의 "김현진"이 그대로 잡힌다. 조사·호칭
 * 표는 빠진 항목이 생기는 순간 조용히 기권으로 바뀌는 종류의 코드다.
 */
public final class AttendeeNameMatcher {

    /*
     * 허용 편집거리. **한 글자까지다.**
     *
     * 두 글자까지 넓히면 실측에서 6 건을 더 잡을 수 있었지만 그 조건의 오답률은 재지 않았다.
     * 지금 값은 "오답 0 건"이 확인된 유일한 값이다.
     */
    public static final int MAX_DISTANCE = 1;

    private AttendeeNameMatcher() {
    }

    /*
     * 글 안에서 가장 가까운 참석자를 찾는다.
     *
     * @param haystack       찾을 글. 발화 하나일 수도, 여러 발화를 이어 붙인 것일 수도 있다
     * @param nameByMemberId 참석자 memberId → 이름
     * @return 판정 결과. 후보가 없으면 {@link Match#none()}
     */
    public static Match find(String haystack, Map<Long, String> nameByMemberId) {
        if (haystack == null || haystack.isBlank() || nameByMemberId == null || nameByMemberId.isEmpty()) {
            return Match.none();
        }

        Long best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tied = false;
        for (Map.Entry<Long, String> entry : nameByMemberId.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            String name = entry.getValue().trim();
            int distance = minDistance(haystack, name);
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

        return best == null ? Match.none() : new Match(best, bestDistance, tied);
    }

    /*
     * 이름 길이에 따라 허용 거리를 줄인다.
     *
     * 두 글자 이름에 한 글자 차이를 허용하면 절반이 달라도 같은 이름으로 보는 것이고, 그
     * 폭이면 무관한 낱말도 걸린다. 세 글자(한국어 성명의 보통 길이) 이상에서만 한 글자를
     * 허용하고, 그보다 짧으면 정확히 같을 때만 잇는다.
     */
    private static int allowedDistance(String name) {
        return name.length() >= 3 ? MAX_DISTANCE : 0;
    }

    /* {@code haystack} 안에서 {@code name} 과 가장 가까운 조각의 편집거리. */
    private static int minDistance(String haystack, String name) {
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
    private static int levenshtein(String left, String right) {
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
     * <b>{@code tied} 를 결과에 남기는 것이 이 기록의 핵심이다.</b> "후보가 없다"와 "후보가
     * 둘인데 못 고르겠다"는 둘 다 기권이지만 뜻이 완전히 다르다 — 앞은 그 글에 이름이 없는
     * 것이고, 뒤는 비슷한 이름의 참석자가 겹쳐 이 규칙의 한계에 닿은 것이다. 뭉치면 참석자
     * 이름이 겹치는 회의에서 왜 판정이 안 되는지 로그로 알 수 없다.
     *
     * @param memberId 가장 가까운 참석자. 후보가 없으면 null
     * @param distance 그 거리. 후보가 없으면 뜻이 없다
     * @param tied     같은 거리의 참석자가 둘 이상인가
     */
    public record Match(Long memberId, int distance, boolean tied) {

        private static final Match NONE = new Match(null, Integer.MAX_VALUE, false);

        public static Match none() {
            return NONE;
        }

        /*
         * 이 판정을 근거로 써도 되는가 — 후보가 있고, 유일할 때만.
         *
         * 동점이면 하나를 고르지 않는다. 참석자가 많은 회의에서 비슷한 이름이 겹칠 수 있고,
         * 그때 하나를 고르면 "오답 0 건"이라는 성질이 깨진다.
         */
        public boolean isUnique() {
            return memberId != null && !tied;
        }
    }
}
