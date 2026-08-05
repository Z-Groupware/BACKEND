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
    L7("L7");

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
