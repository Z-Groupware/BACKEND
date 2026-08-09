package com.module06.backend.capture.domain.model;

/*
 * L1.5 가 판정한 지시어의 대상 종류다.
 *
 * 종류를 돌려받는 이유는 소비자가 다르기 때문이다 — PERSON 은 L4 의 담당자 판정에,
 * TOPIC·ARTIFACT 는 항목 본문에 쓰인다. 하나로 뭉치면 "그분"과 "그거"를 같은 방식으로
 * 처리하게 되고, 사람이 아닌 것이 담당자 후보로 올라간다.
 *
 * UNRESOLVED 는 실패가 아니라 **기권**이다. 모델이 스스로 말한 확신도(confidence)를 받지 않는
 * 대신 이 값을 쓴다 — 자기보고 확신도는 85~95 에 몰려 실제 정확도와 맞지 않는다.
 * 기권을 오류로 취급하면 계층이 억지로 답을 만들게 되고, 그게 WRONG_ASSIGNEE 의 출처가 된다.
 */
public enum ReferenceType {

    /* 사람을 가리킨다. resolvedPersonId 가 채워지면 명단 안, null 이면 명단 밖이다. */
    PERSON,

    /* 앞서 논의된 주제를 가리킨다("아까 그 얘기"). */
    TOPIC,

    /* 산출물·문서를 가리킨다("그 문서", "그거"). */
    ARTIFACT,

    /* 시점을 가리킨다("그때까지"). */
    TIME,

    /* 해소하지 못했다. 기권이며 정상 동작이다. */
    UNRESOLVED
}
