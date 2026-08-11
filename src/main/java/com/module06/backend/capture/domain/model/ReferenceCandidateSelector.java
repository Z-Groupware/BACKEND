package com.module06.backend.capture.domain.model;

import java.util.ArrayList;
import java.util.List;

/*
 * L1.5(지시어 해소)가 볼 발화를 고른다.
 *
 * <h2>지금까지 항상 비워 보냈다</h2>
 * {@code AiLayerPort#resolveReference} 의 targetUtteranceIds 는 "비워 보내면 전체가 대상"이고,
 * 후보를 고르는 코드가 없어 늘 비어 있었다. 그래서 모델이 회의의 모든 발화에서 지시어를 찾았고,
 * 응답 스키마의 utteranceId enum 에도 전체 발화가 들어갔다.
 *
 * <h2>⚠ 입력 토큰은 줄지 않는다 — 명세의 전제를 정정한다</h2>
 * 남은작업 문서는 이 항목을 "토큰 비용과 정확도 양쪽에 손해"로 적었다. 확인해 보니 **입력 쪽은
 * 그렇지 않다.** Python 은 target 과 무관하게 발화 전체를 프롬프트에 싣는다
 * (app/layers/l1_5.py 의 {@code format_utterances(request.utterances, ...)}) — 선행사가 주제
 * 경계를 넘으므로 문맥은 통째로 있어야 하고, 그건 이 계층을 회의당 한 번만 부르는 이유이기도 하다.
 *
 * target 이 바꾸는 것은 둘이다.
 *   ① **응답 스키마의 enum** 이 후보로 좁혀진다 — 지시어가 없는 발화에 해소를 붙일 수 없게 된다
 *   ② 프롬프트에서 대상 발화가 표시된다(TARGET_MARK)
 * 즉 이득은 **정확도와 출력 토큰**이다. 입력 토큰을 줄이려면 계층 호출 자체를 건너뛰어야 하고,
 * 그건 이 선별기가 하나도 놓치지 않는다는 확신이 필요해 지금 하지 않는다.
 *
 * <h2>재현율을 정확도보다 앞세운다</h2>
 * 두 방향의 실패가 대칭이 아니다.
 *   · 후보를 **빠뜨리면** 그 지시어는 아예 풀릴 기회가 없다. Python 이 후보 밖 utteranceId 를
 *     응답에서 버리기 때문에(parse_references) 조용히 사라지고, 그 발화를 근거로 만들어진
 *     액션은 "제가"가 누군지 모르는 채로 남는다.
 *   · 후보를 **더 넣으면** 모델이 그 발화에서 아무것도 못 찾고 넘어간다. 출력 토큰이 조금 늘 뿐이다.
 * 그래서 애매하면 넣는다. 이 저장소가 "한쪽으로만 틀리게 만든다"고 적어 온 것과 같은 방향이다.
 *
 * <h2>형태소 분석을 하지 않는다</h2>
 * 조사가 붙는 말이라("그거를", "저쪽이") 어간 포함 검사로 잡는다. 분석기를 들이면 사전·모델
 * 의존이 하나 더 생기고, 여기서 필요한 판정은 "지시어가 있을 법한가"까지다 — 실제 해소는
 * 모델이 한다. 애매한 것을 넣는 쪽이라 정밀한 판정이 필요하지 않다.
 */
public final class ReferenceCandidateSelector {

    /*
     * 사람을 가리키는 표현(PERSON).
     *
     * ⚠ 맨 "저"를 넣지 않는다 — "저장", "저번", "저희"에 걸려 거의 모든 발화가 후보가 된다.
     * 그러면 좁히는 의미가 사라진다. 대신 조사가 붙은 형태를 나열한다.
     *
     * 호칭(…님·…씨)도 넣는다. 설계 문서가 L1.5 의 대상으로 "제가 / 저쪽 / 김대리님"을 함께
     * 들었고, 이름을 부른 발화가 곧 담당자 지정인 경우가 많다.
     */
    private static final List<String> PERSON_MARKERS = List.of(
            "제가", "제 ", "저는", "저도", "저희", "우리", "본인",
            "그분", "이분", "저분", "그쪽", "이쪽", "저쪽",
            "님이", "님은", "님께", "님한테", "님이랑", "님과", "씨가", "씨는",
            "담당자", "그 사람", "그사람");

    /* 앞서 논의된 주제를 가리키는 표현(TOPIC). */
    private static final List<String> TOPIC_MARKERS = List.of(
            "아까", "방금", "앞에서", "먼저 말", "말씀하신", "얘기한", "이야기한",
            "그 얘기", "그얘기", "그 이야기", "그 부분", "그부분", "그 건", "그건",
            "이 부분", "이부분", "그 주제", "위에서");

    /* 산출물·문서를 가리키는 표현(ARTIFACT). */
    private static final List<String> ARTIFACT_MARKERS = List.of(
            "그거", "그것", "이거", "이것", "저거", "저것", "그걸", "이걸", "저걸",
            "그 문서", "그문서", "그 파일", "그파일", "그 자료", "그자료",
            "해당 문서", "위 문서", "그 링크");

    /* 시점을 가리키는 표현(TIME). */
    private static final List<String> TIME_MARKERS = List.of(
            "그때", "그 때", "그날", "그 날", "그 전에", "그전에", "그 이후", "그이후",
            "다음 주까지", "그 일정", "그날짜", "그 날짜", "그 기한", "말한 날");

    private ReferenceCandidateSelector() {
    }

    /*
     * 지시어가 있을 법한 발화의 id 를 고른다.
     *
     * <h2>비우는 것과 고르는 것을 구분한다</h2>
     * 하나도 안 걸리면 **빈 목록**을 돌려준다. 그러면 호출자가 그대로 넘기고 Python 은 "비워
     * 보냈으니 전체가 대상"으로 읽는다 — 예전과 같은 동작이다.
     *
     * 여기서 "후보 0건이니 계층을 건너뛰자"로 가지 않는다. 어간 목록이 하나도 못 잡는 회의는
     * 지시어가 정말 없는 회의일 수도 있고 **내 목록이 놓친 회의일 수도 있는데**, 둘을 구분할
     * 방법이 지금 없다. 건너뛰면 후자에서 그 회의의 지시어가 통째로 안 풀리고, 그건 조용한
     * 실패다. 검토 로그가 "선별기가 놓친 적 없다"를 보여준 뒤에 열 문이다.
     *
     * @param utterances 정본 발화. id 나 본문이 없는 것은 건너뛴다
     * @return 후보 발화 id. 정본 순서를 유지한다
     */
    public static List<Long> select(List<Utterance> utterances) {
        if (utterances == null || utterances.isEmpty()) {
            return List.of();
        }

        List<Long> candidates = new ArrayList<>();
        for (Utterance utterance : utterances) {
            if (utterance.utteranceId() == null) {
                continue;
            }
            String text = utterance.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (hasAnyMarker(text)) {
                candidates.add(utterance.utteranceId());
            }
        }
        return List.copyOf(candidates);
    }

    /*
     * 네 종류를 한 번에 본다.
     *
     * 종류를 나눠 돌려주지 않는 이유 — 포트가 받는 것은 id 목록 하나이고, 어느 종류인지는
     * **모델이 판정한다**(referenceType). 우리가 미리 나누면 그 판정을 앞질러 정해 버리고,
     * 틀렸을 때 모델이 바로잡을 자리가 없다.
     */
    private static boolean hasAnyMarker(String text) {
        return containsAny(text, PERSON_MARKERS)
                || containsAny(text, TOPIC_MARKERS)
                || containsAny(text, ARTIFACT_MARKERS)
                || containsAny(text, TIME_MARKERS);
    }

    private static boolean containsAny(String text, List<String> markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
