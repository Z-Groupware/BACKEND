package com.module06.backend.meeting.infrastructure.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.module06.backend.meeting.application.port.out.MemberQueryPort;

/*
 * 구성원 도메인의 배치 조회 Port가 연결되기 전까지 사용하는 명시적 pending 어댑터다.
 *
 * 참석자 이름이나 회사 소속을 추측하지 않으며 호출 시 연동 필요성을 즉시 드러낸다.
 */
@Component
public class PendingMemberQueryAdapter implements MemberQueryPort {

    /* 구성원 담당 도메인의 배치 조회 계약이 아직 연결되지 않았음을 알린다. */
    @Override
    public List<MemberSnapshot> findActiveMembers(Long companyId, List<Long> memberIds) {
        /* 빈 목록으로 통과시키면 정상 참석자까지 MT-010이 되어 원인이 가려지므로 명시적으로 실패한다. */
        throw new UnsupportedOperationException(
                "MemberQueryPort 연동 대기 중입니다. B(member) 도메인의 구성원 배치 조회 구현이 필요합니다."
        );
    }
}
