package com.module06.backend.capture.domain.model;

/*
 * L4 가 담당자를 그렇게 정한 근거다.
 *
 * 근거를 값으로 남기는 이유 — 두 경로의 신뢰도가 다르다. 이름을 불러 지시한 것과
 * 스스로 하겠다고 한 것 중 어느 쪽이 더 자주 틀리는지 알아야 L7 자동확정 게이트의 조건을
 * 정할 수 있다. 근거를 버리면 "담당자가 틀렸다"만 남고 어느 경로를 조일지 모른다.
 *
 * null 이 판정 불가다. Python 은 구조화 출력에서 nullable+enum 조합을 피하려고 'UNKNOWN'
 * 값으로 받아 후처리에서 None 으로 바꿔 보낸다 — 그 None 이 여기 null 로 온다.
 * 그래서 이 enum 에 UNKNOWN 상수를 두지 않는다(전송 표현과 도메인 표현을 섞지 않는다).
 */
public enum AssigneeSource {

    /* 이름을 불러 지시했다("김서준님이 해주세요"). */
    EXPLICIT_CALL,

    /* 스스로 하겠다고 했다("제가 할게요"). 화자 귀속(L1)이 없으면 이 경로는 담당자를 모른다. */
    FIRST_PERSON;

    /*
     * 알 수 없는 값은 null 이다. 임의로 하나를 고르면 근거가 조작되고, 그 값으로 게이트
     * 조건을 정하게 된다.
     */
    public static AssigneeSource fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (AssigneeSource source : values()) {
            if (source.name().equals(value)) {
                return source;
            }
        }
        return null;
    }
}
