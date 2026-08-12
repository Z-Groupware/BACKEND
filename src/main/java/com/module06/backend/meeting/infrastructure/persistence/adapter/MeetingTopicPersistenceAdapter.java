package com.module06.backend.meeting.infrastructure.persistence.adapter;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meeting.domain.model.MeetingAgenda;
import com.module06.backend.meeting.domain.model.MeetingTopicType;
import com.module06.backend.meeting.domain.repository.MeetingTopicRepository;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingTopicJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingTopicRepository;

/*
 * MEET-01의 대주제와 소주제 계층을 meeting_topic 테이블에 저장하는 JPA 어댑터다.
 */
@Component
@RequiredArgsConstructor
public class MeetingTopicPersistenceAdapter implements MeetingTopicRepository {

    /* meeting_topic 행을 생성하고 MAIN 식별자를 확보하는 기술 저장소다. */
    private final SpringDataMeetingTopicRepository springDataMeetingTopicRepository;

    /* MAIN을 먼저 저장한 뒤 그 식별자를 SUB의 parent_topic_id로 사용한다. */
    @Override
    public void saveAgenda(Long meetingId, MeetingAgenda agenda) {
        /* 대주제는 회의 안건의 첫 번째 항목이며 부모가 없다. */
        MeetingTopicJpaEntity mainTopic = springDataMeetingTopicRepository.saveAndFlush(
                new MeetingTopicJpaEntity(
                        meetingId,
                        null,
                        MeetingTopicType.MAIN,
                        agenda.mainTopic(),
                        0
                )
        );

        /* 소주제는 입력 순서를 유지하고 모두 저장된 MAIN 식별자를 부모로 가진다. */
        List<MeetingTopicJpaEntity> subTopics = IntStream.range(0, agenda.subTopics().size())
                .mapToObj(index -> new MeetingTopicJpaEntity(
                        meetingId,
                        mainTopic.getId(),
                        MeetingTopicType.SUB,
                        agenda.subTopics().get(index),
                        index + 1
                ))
                .toList();

        /* 같은 트랜잭션 안에서 소주제 전체를 한 번에 저장한다. */
        springDataMeetingTopicRepository.saveAll(subTopics);
    }
}
