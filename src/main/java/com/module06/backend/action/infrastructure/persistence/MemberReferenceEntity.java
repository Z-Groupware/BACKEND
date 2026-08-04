package com.module06.backend.action.infrastructure.persistence;

/* comment.
    identity(B, 윤종호) 소유 member 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    존재 이유: 개인 액션 상세·목록에서 담당자를 id만으로 보여줄 수 없고, B의 엔티티를
    직접 참조하면 0절 1항 위반이다. 조회할 컬럼은 id·name 정도다.
    쓰기 금지. C는 권한 판단을 role 컬럼이 아니라 JWT authority로 하므로 이 엔티티와 권한 로직은 무관하다.

    연결된 클래스
    - ActionService     : 담당자 이름 표시(GetActionDetailUseCase 구현)
    - ActionJpaEntity   : assignee_member_id 조인의 반대편
*/
public class MemberReferenceEntity {
}
