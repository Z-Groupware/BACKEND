package com.module06.backend.capture.domain.model;

/*
 * L3 가 주제에서 뽑아낸 항목 하나다. meeting_decision 한 행에 그대로 대응한다.
 *
 * reason 을 필수로 들고 다닌다. L3 가 왜 이 항목을 결정으로 분류했는지 남기지 않으면,
 * 나중에 오분류를 발견해도 프롬프트의 어느 부분이 문제였는지 되짚을 수 없다(V5.8 주석).
 *
 * gateStatus 가 여기 없는 것이 의도다. 그건 L3.5 의 산출이고, L3 결과에 미리 자리를 만들어 두면
 * "아직 게이트를 안 지났다"와 "게이트가 논의로 판정했다"가 같은 null 로 보인다.
 */
public record TopicItem(
        ItemType itemType,
        String content,
        String reason,
        Long evidenceUtteranceId
) {
}
