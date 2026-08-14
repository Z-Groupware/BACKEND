package com.module06.backend.meeting.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.repository.StalledSummaryMeetingRepository;
import com.module06.backend.meeting.domain.repository.StalledSummaryMeetingRepository.StalledSummaryMeetingCandidate;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;

/*
 * MEET-15 요약 중단 후보 조회의 회사·host·종료 상태 필터와 MEET-18 온라인 회의 제외를 실제 JPA로 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("MEET-15 요약 중단 후보 영속성 어댑터")
class StalledSummaryMeetingPersistenceAdapterTest {

    /* 애플리케이션 계층이 사용하는 실제 후보 조회 저장소 계약이다. */
    @Autowired
    private StalledSummaryMeetingRepository stalledSummaryMeetingRepository;

    /* 테스트 회의 행을 저장하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingRepository springDataMeetingRepository;

    /* 각 테스트가 독립된 회의 데이터로 실행되도록 meeting 테이블을 초기화한다. */
    @BeforeEach
    void clearMeetingData() {
        springDataMeetingRepository.deleteAll();
    }

    /*
     * MEET-18 온라인 회의는 startAt이 없어 이 화면의 시작일 필터·정렬과 맞지 않으므로,
     * 같은 회사·host·DONE 상태라도 후보에서 제외돼야 한다.
     */
    @Test
    @DisplayName("같은 회사·host·DONE 상태여도 온라인 회의는 후보에서 제외한다")
    void excludesOnlineMeetings() {
        MeetingJpaEntity hostedDone = springDataMeetingRepository.save(
                doneMeeting(10L, 5L, 3L, "물리 회의", LocalDateTime.of(2026, 8, 10, 14, 0))
        );
        springDataMeetingRepository.save(onlineDoneMeeting(10L, 5L, 3L, "온라인 회의"));

        List<StalledSummaryMeetingCandidate> candidates =
                stalledSummaryMeetingRepository.findHostedDoneSummaryCandidates(10L, 3L);

        assertThat(candidates)
                .extracting(StalledSummaryMeetingCandidate::meetingId)
                .containsExactly(hostedDone.getId());
    }

    /* 회사·host·DONE 상태로 종료된 물리 회의 엔티티를 만든다. */
    private MeetingJpaEntity doneMeeting(
            Long companyId,
            Long projectId,
            Long hostMemberId,
            String title,
            LocalDateTime startAt
    ) {
        Meeting scheduled = Meeting.create(
                companyId,
                projectId,
                100L,
                2L,
                hostMemberId,
                title,
                startAt,
                startAt.plusHours(1),
                false,
                null,
                List.of(hostMemberId)
        );
        Meeting entered = scheduled.enter(startAt);
        Meeting completed = entered.complete(startAt.plusHours(1));
        return MeetingJpaEntity.from(completed);
    }

    /* MEET-18 온라인 회의를 개설 즉시 DONE 상태로 만든다 — meetingRoomId·startAt·endAt이 없다. */
    private MeetingJpaEntity onlineDoneMeeting(
            Long companyId,
            Long projectId,
            Long hostMemberId,
            String title
    ) {
        Meeting created = Meeting.createOnline(
                companyId,
                projectId,
                100L,
                hostMemberId,
                title,
                true,
                null,
                List.of()
        );
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 10, 0);
        Meeting entered = created.enter(now);
        Meeting completed = entered.complete(now);
        return MeetingJpaEntity.from(completed);
    }
}
