package com.module06.backend.capture.application.service;

import java.util.List;

import com.module06.backend.capture.application.port.out.AiLayerPort;

/*
 * 계층에 넘길 참석자 명단(닫힌 목록)을 만든다.
 *
 * 명단이 곧 응답 스키마의 enum 이 된다 — 목록 밖 personId 가 나올 방법 자체가 없어진다.
 * 그래서 이 목록이 틀리면 정확도 4원칙의 '닫힌 목록'이 프롬프트 부탁으로 내려앉는다.
 *
 * 참석자 명단의 주인은 회의 기능(D)이다. 여기서는 읽기만 한다 — meetingroom 도메인이
 * MeetingAttendeeReferenceEntity 로 같은 테이블을 읽는 것과 같은 방식이다.
 */
public interface MeetingParticipantProvider {

    /*
     * @return 회의 참석자. **unknown_person 탈출구(personId=null)를 반드시 포함한다** —
     *         없으면 모델이 명단 안에서 억지로 하나를 고르고, 그 값은 검증도 안 된다.
     */
    List<AiLayerPort.Participant> participantsOf(long meetingId);
}
