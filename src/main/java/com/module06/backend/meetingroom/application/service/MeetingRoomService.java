package com.module06.backend.meetingroom.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;
import com.module06.backend.meetingroom.application.usecase.GetMeetingRoomListUseCase;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomRepository;

/*
 * 회의실 애그리거트의 애플리케이션 유스케이스를 구현하는 서비스다.
 *
 * 이번 ROOM-01 범위에서는 회사별 활성 회의실 조회만 구현하며,
 * 이후 회의실 상세·등록·수정·비활성화 UseCase도 같은 서비스에 연결할 수 있다.
 * 조회 트랜잭션에서는 데이터 변경 감지를 줄이기 위해 읽기 전용 속성을 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingRoomService implements GetMeetingRoomListUseCase {

    /* 기술 구현과 분리된 회의실 도메인 저장소 계약이다. */
    private final MeetingRoomRepository meetingRoomRepository;

    /*
     * 요청자의 회사에 속한 활성 회의실을 조회해 API 전달용 결과로 변환한다.
     * Repository가 정렬과 활성 상태 필터링을 보장하므로 서비스는 결과 변환만 담당한다.
     *
     * @param companyId 인증된 요청자의 회사 식별자
     * @return 회의실 요약 목록, 조회 결과가 없으면 빈 목록
     */
    @Override
    public List<MeetingRoomSummary> getMeetingRooms(Long companyId) {
        /* 조회한 도메인 객체마다 ROOM-01에 필요한 속성만 뽑아 불변 결과 목록으로 반환한다. */
        return meetingRoomRepository.findAllActiveByCompanyId(companyId).stream()
                .map(MeetingRoomSummary::from)
                .toList();
    }
}
