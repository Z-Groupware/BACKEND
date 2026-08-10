package com.module06.backend.notification.application.port.out;

import java.util.List;

/*
 * 회사 전체 알림의 수신자를 조회하는 notification 소유 아웃바운드 Port다.
 * notification은 identity 엔티티를 직접 참조하지 않고 필요한 회원 식별자만 전달받는다.
 */
public interface CompanyMemberQueryPort {

    /* 퇴사 처리되지 않은 회사 구성원의 식별자를 한 번에 조회한다. */
    List<Long> findActiveMemberIds(Long companyId);
}
