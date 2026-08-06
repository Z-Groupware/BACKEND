package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;

/*
 * CAP 회의 참조 어댑터의 isHost 판정(meeting.host_member_id 비교)을 실제 JPA로 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("CAP 회의 참조 어댑터 — isHost")
class MeetingReferenceRepositoryAdapterTest {

    @Autowired
    private MeetingReferenceRepository meetingReferenceRepository;

    @Autowired
    private SpringDataMeetingRepository springDataMeetingRepository;

    @BeforeEach
    void clear() {
        springDataMeetingRepository.deleteAll();
    }

    /* host_member_id와 일치하는 사람만 isHost가 true인지 검증한다. */
    @Test
    @DisplayName("회의 담당자만 isHost가 true다")
    void isHostOnlyForHost() {
        // 담당자(host)가 3번인 회의를 저장한다.
        Long meetingId = springDataMeetingRepository.save(MeetingJpaEntity.from(Meeting.create(
                10L, 12L, null, 2L, 3L, "수동 업로드 대상 회의",
                LocalDateTime.of(2026, 8, 6, 14, 0), LocalDateTime.of(2026, 8, 6, 15, 0),
                false, null, List.of(3L)))).getId();

        assertThat(meetingReferenceRepository.isHost(meetingId, 3L)).isTrue();
        assertThat(meetingReferenceRepository.isHost(meetingId, 9L)).isFalse();
        // 존재하지 않는 회의는 false.
        assertThat(meetingReferenceRepository.isHost(999_999L, 3L)).isFalse();
    }
}
