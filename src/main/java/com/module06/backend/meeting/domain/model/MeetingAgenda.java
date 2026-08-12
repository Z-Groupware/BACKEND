package com.module06.backend.meeting.domain.model;

import java.util.List;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.exception.MeetingErrorCode;

/*
 * MEET-01에서 회의와 함께 확정되는 대주제와 소주제 묶음이다.
 *
 * 화면 검증을 우회한 내부 호출에서도 MAIN 한 개와 SUB 한 개 이상의 계약을 지킨다.
 */
public record MeetingAgenda(
        String mainTopic,
        List<String> subTopics
) {

    /* meeting_topic.content 컬럼과 동일한 최대 길이다. */
    private static final int MAX_TOPIC_LENGTH = 300;

    /* 대주제와 소주제를 정규화하고 안건 불변 조건을 검증한다. */
    public static MeetingAgenda create(String mainTopic, List<String> subTopics) {
        /* 대주제는 공백이 아니고 데이터베이스 길이 제한을 넘지 않아야 한다. */
        if (isInvalidTopic(mainTopic)) {
            throw new BusinessException(MeetingErrorCode.INVALID_MEETING_AGENDA);
        }

        /* 소주제는 하나 이상이며 각 항목이 공백 또는 길이 초과 값이면 안 된다. */
        if (subTopics == null || subTopics.isEmpty() || subTopics.stream().anyMatch(MeetingAgenda::isInvalidTopic)) {
            throw new BusinessException(MeetingErrorCode.INVALID_MEETING_AGENDA);
        }

        /* 앞뒤 공백을 제거해 같은 화면 문자열이 저장 위치마다 다르게 보이지 않게 한다. */
        return new MeetingAgenda(
                mainTopic.trim(),
                subTopics.stream().map(String::trim).toList()
        );
    }

    /* null·공백·컬럼 길이 초과 여부를 한 규칙으로 판정한다. */
    private static boolean isInvalidTopic(String topic) {
        /* trim 전 입력 길이를 기준으로 데이터베이스 저장 실패 가능성까지 차단한다. */
        return topic == null || topic.isBlank() || topic.length() > MAX_TOPIC_LENGTH;
    }

    /* 외부에서 소주제 목록을 바꾸지 못하도록 불변 복사한다. */
    public MeetingAgenda {
        subTopics = List.copyOf(subTopics);
    }
}
