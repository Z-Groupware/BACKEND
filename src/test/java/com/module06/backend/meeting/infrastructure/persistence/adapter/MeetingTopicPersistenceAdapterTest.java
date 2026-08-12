package com.module06.backend.meeting.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.module06.backend.meeting.domain.model.MeetingAgenda;
import com.module06.backend.meeting.domain.model.MeetingTopicType;
import com.module06.backend.meeting.domain.repository.MeetingTopicRepository;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingTopicJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingTopicRepository;

/*
 * MEET-01 안건 저장 어댑터가 MAIN·SUB 계층과 입력 순서를 실제 JPA로 보존하는지 검증한다.
 */
@SpringBootTest
@DisplayName("MEET-01 회의 안건 영속성 어댑터")
class MeetingTopicPersistenceAdapterTest {

    /* 애플리케이션 계층이 사용하는 실제 안건 저장 계약이다. */
    @Autowired
    private MeetingTopicRepository meetingTopicRepository;

    /* 저장 결과를 조회하고 테스트 데이터를 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingTopicRepository springDataMeetingTopicRepository;

    /* 각 테스트가 이전 안건 계층을 공유하지 않도록 초기화한다. */
    @BeforeEach
    void clearTopics() {
        /* 자식과 부모가 같은 테이블에 있으므로 저장소 전체 삭제로 순서를 위임한다. */
        springDataMeetingTopicRepository.deleteAll();
    }

    /* MAIN 식별자가 모든 SUB의 부모로 저장되는지 검증한다. */
    @Test
    @DisplayName("MAIN 한 건과 이를 부모로 하는 SUB 목록을 순서대로 저장한다")
    void savesMainAndSubTopicHierarchy() {
        /* 대주제 한 건과 순서가 중요한 소주제 두 건을 준비한다. */
        MeetingAgenda agenda = MeetingAgenda.create(
                "스프린트 진행 상황",
                List.of("개발 진행률 점검", "배포 일정 합의")
        );

        /* 존재하는 회의 식별자라고 가정하고 안건 계층 저장을 실행한다. */
        meetingTopicRepository.saveAgenda(91L, agenda);

        /* 회의와 표시 순서 기준으로 저장된 MAIN·SUB 행을 조회한다. */
        List<MeetingTopicJpaEntity> topics = springDataMeetingTopicRepository
                .findAllByMeetingIdInOrderByMeetingIdAscSortOrderAscIdAsc(List.of(91L));

        /* MAIN이 먼저이고 뒤의 SUB 내용이 요청 순서를 유지해야 한다. */
        assertThat(topics)
                .extracting(MeetingTopicJpaEntity::getTopicType)
                .containsExactly(MeetingTopicType.MAIN, MeetingTopicType.SUB, MeetingTopicType.SUB);
        assertThat(topics)
                .extracting(MeetingTopicJpaEntity::getContent)
                .containsExactly("스프린트 진행 상황", "개발 진행률 점검", "배포 일정 합의");

        /* 모든 SUB는 방금 생성된 MAIN의 식별자를 부모로 참조해야 한다. */
        Long mainTopicId = topics.get(0).getId();
        assertThat(topics.subList(1, topics.size()))
                .extracting(MeetingTopicJpaEntity::getParentTopicId)
                .containsOnly(mainTopicId);
    }
}
