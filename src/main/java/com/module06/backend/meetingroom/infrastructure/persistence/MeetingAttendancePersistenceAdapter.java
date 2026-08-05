package com.module06.backend.meetingroom.infrastructure.persistence;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meetingroom.domain.repository.MeetingAttendanceRepository;

/*
 * MeetingAttendanceRepository 도메인 계약을 JPA로 구현하는 아웃바운드 어댑터다.
 *
 * 참석 여부 판단에 필요한 회의 식별자 집합만 만들어 반환하며, 참석자 명단 자체는 상위 계층으로 넘기지 않는다.
 * 회의 도메인이 참석자 조회 포트를 제공하면 이 어댑터 안의 조회만 교체하면 된다.
 */
@Component
@RequiredArgsConstructor
public class MeetingAttendancePersistenceAdapter implements MeetingAttendanceRepository {

    /* 실제 meeting_attendee 조회 쿼리를 실행하는 읽기 전용 기술 저장소다. */
    private final SpringDataMeetingAttendeeReferenceRepository springDataMeetingAttendeeReferenceRepository;

    /*
     * 주어진 회의 중 요청자가 참석자인 회의의 식별자 집합을 반환한다.
     *
     * @param memberId 인증된 요청자의 구성원 식별자
     * @param meetingIds 참석 여부를 확인할 회의 식별자 목록
     * @return 요청자가 참석자인 회의 식별자 집합, 확인 대상이 없으면 빈 집합
     */
    @Override
    public Set<Long> findAttendedMeetingIds(Long memberId, List<Long> meetingIds) {
        /* 요청자를 특정할 수 없거나 확인할 회의가 없으면 조회 없이 빈 집합으로 응답한다. */
        if (memberId == null || meetingIds == null || meetingIds.isEmpty()) {
            return Set.of();
        }

        /* 조회 결과를 집합으로 바꿔 슬롯마다 반복되는 참석 여부 확인을 상수 시간에 처리한다. */
        return Set.copyOf(springDataMeetingAttendeeReferenceRepository.findAttendedMeetingIds(memberId, meetingIds));
    }
}
