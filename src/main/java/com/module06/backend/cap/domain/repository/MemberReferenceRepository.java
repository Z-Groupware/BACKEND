package com.module06.backend.cap.domain.repository;

import java.util.List;

/* comment.
    identity(윤종호) 소유 member 테이블을 읽기 전용으로 조회하는 계약. CAP-13 SSE의 caption 이벤트가
    발신자 이름을 화면에 보여줘야 하는데 id만으로는 부족하다(action.MemberReferenceRepository와 동일 이유,
    같은 회사 다른 도메인이 이미 쓰는 패턴).
*/
public interface MemberReferenceRepository {

    /** 발신자 이름 배치 조회 — SSE 브로드캐스트 한 번에 여러 발신자가 섞여 있을 수 있어 배치로 묶는다. */
    List<MemberName> findNames(List<Long> memberIds);

    record MemberName(Long memberId, String name) {
    }
}
