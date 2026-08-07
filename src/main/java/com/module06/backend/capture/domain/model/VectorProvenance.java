package com.module06.backend.capture.domain.model;

/*
 * few-shot 예시의 출처다(meeting_tuple_vector.provenance · V5.10).
 *
 * **AUTO 를 예시로 쓰면 모델이 자기 출력을 다시 학습하는 루프가 생긴다.** 처음에 틀린 배정
 * 하나가 예시로 뽑히면 그 뒤 회의들이 같은 모양으로 틀리고, 그 결과가 다시 예시가 된다.
 * 그래서 AI-09 는 provenance 를 필수 필터로 요구한다.
 */
public enum VectorProvenance {

    /* 사람이 검토 화면에서 확인·수정한 값. 예시로 쓸 수 있는 유일한 종류다. */
    HUMAN_VERIFIED,

    /* 모델이 낸 값을 그대로 담은 것. 예시로 쓰지 않는다. */
    AUTO
}
