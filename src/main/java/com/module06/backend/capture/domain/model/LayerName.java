package com.module06.backend.capture.domain.model;

import java.util.Arrays;

/*
 * 분석 계층의 이름이다.
 *
 * DB(analysis_layer.layer)와 Python 내부 API가 쓰는 문자열은 "L1.5"·"L3.5"처럼 점을 포함하는데
 * 자바 enum 상수 이름에는 점을 쓸 수 없다. 그래서 상수 이름과 전송 값을 분리한다 —
 * name()을 그대로 저장하면 DB에 "L1_5"가 들어가고, 같은 계층이 파이썬 쪽 "L1.5"와 갈린다.
 *
 * analysis_layer.layer 를 ENUM 이 아니라 VARCHAR 로 둔 이유(V5.6 주석)와 짝이다.
 * 중간 계층이 늘어날 여지가 있고, 계층 추가마다 공용 ALTER 를 도는 것보다 낫다.
 *
 * 코드 계층(L1·L6·L7)도 여기 둔다. LLM 을 쓰지 않지만 analysis_layer 에는 같이 기록되고,
 * CAP-06 이 그 상태까지 함께 내려줘야 사용자가 "어디까지 됐는지"를 볼 수 있다.
 */
public enum LayerName {

    /* 화자 귀속. 코드 계층 — 자막 rms 비교로 판정한다(LLM 아님). */
    L1("L1"),

    /* 지시어 해소. AI-02. */
    L1_5("L1.5"),

    /* 주제 분할. AI-03. */
    L2("L2"),

    /* 주제별 정리. AI-04 — 주제마다 한 번씩 호출된다. */
    L3("L3"),

    /* 확정/논의 게이트. AI-05. */
    L3_5("L3.5"),

    /* assignment tuple 추출. AI-06. */
    L4("L4"),

    /* 관점 다변화 검증. AI-07. */
    L5("L5"),

    /* 규칙·모순 검사. 코드 계층. */
    L6("L6"),

    /* 자동확정 게이트(4조건). 코드 계층 — 모델이 말한 확신도를 쓰지 않는다. */
    L7("L7"),

    /*
     * 액션 분배. **계층이 아니라 파이프라인의 마지막 단계다** — 모델을 부르지 않고, tuple 을
     * C 도메인의 action 으로 넘긴다.
     *
     * 그래도 여기에 두는 이유는 셋이다.
     *   1. 잠금  — analysis_layer 의 UNIQUE(meeting_id, layer)가 중복 분배를 막는다.
     *              SQS 는 at-least-once 라 같은 회의가 두 번 들어오는 일이 언젠가 반드시 있고,
     *              그때 만들어지는 것은 사람 보드에 꽂히는 액션이다.
     *   2. 상태  — 분배가 실패했는지 CAP-06 으로 보인다. 계층 밖에서 부르면 "분석 완료인데
     *              액션이 없는" 회의가 왜 그런지 아무 데도 남지 않는다.
     *   3. 완료 판정 — 액션이 만들어지지 않은 회의는 완료가 아니다(RUN_LAYERS).
     *
     * L1~L7 과 달리 계층 번호를 붙이지 않았다. 번호를 붙이면 명세의 「L1~L7」과 갈리고,
     * AI 계층이 하나 더 있는 것처럼 읽힌다.
     */
    DIST("DIST"),

    /*
     * 회의 개요. **파이프라인의 마지막이고, 완료 판정에는 들지 않는다.**
     *
     * <h2>왜 DIST 뒤인가</h2>
     * 개요는 이 파이프라인의 산출물 중 가장 덜 중요하다. L3 앞에 두면 개요 생성이 실패했을 때
     * 주제 분할·tuple 추출·액션 분배가 통째로 막힌다 — 읽을 문장 하나 때문에 사람 보드에
     * 할 일이 안 꽂히는 것이다. 그래서 값이 다 만들어진 뒤 맨 끝에 붙인다.
     *
     * <h2>완료 판정에서 빠지는 이유</h2>
     * AnalysisOrchestrator 의 REQUIRED_FOR_DONE 에 넣지 않는다. 넣으면 개요 생성 실패가
     * 회의 전체를 「미완」으로 만들고, 그러면 ANLZ-01 재실행이 **모든 계층의 토큰을 다시 태운다** —
     * 표시용 문장 하나 때문에. 실패해도 개요 칸은 비지 않는다(L3 가 이어 붙인 값이 남아 있다).
     *
     * <h2>번호를 붙이지 않았다</h2>
     * DIST 와 같은 이유다. 명세의 「L1~L7」과 갈리고 AI 계층이 하나 더 있는 것처럼 읽힌다.
     * 이름이 8자라 analysis_layer.layer(VARCHAR(8))에 정확히 들어간다 — 더 긴 이름을 붙이려면
     * 그 컬럼을 함께 넓혀야 한다.
     */
    OVERVIEW("OVERVIEW");

    private final String wireValue;

    LayerName(String wireValue) {
        this.wireValue = wireValue;
    }

    /* DB·API 에 실리는 값이다. name() 대신 반드시 이것을 쓴다. */
    public String wireValue() {
        return wireValue;
    }

    /*
     * 저장된 문자열을 되돌린다.
     *
     * 알 수 없는 값은 예외로 드러낸다. null 이나 기본값으로 넘기면 계층 하나가 조용히
     * 사라진 채 "분석 완료"로 보이는데, 그건 이 파이프라인에서 가장 위험한 실패 방향이다.
     */
    public static LayerName fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(layer -> layer.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 계층입니다: " + value));
    }
}
