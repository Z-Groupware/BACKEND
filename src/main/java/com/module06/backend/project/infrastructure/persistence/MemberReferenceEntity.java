package com.module06.backend.project.infrastructure.persistence;

/* comment.
    identity(B, 윤종호) 소유 member 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    존재 이유: 개설자·업로더를 화면에 이름으로 보여줘야 하는데 id만으로는 부족하고,
    B의 엔티티를 직접 참조하면 0절 1항 위반이다. 조회할 컬럼은 id·name 정도다.
    쓰기 금지. member.role이 다중값으로 바뀌는 변경(B 담당)은 이 엔티티와 무관하다 —
    C는 권한 판단을 role 컬럼이 아니라 JWT authority로 하기 때문이다.

    연결된 클래스
    - GetProjectDetailService  : 개설자 이름 표시
    - ConfirmAttachmentService : 업로더 이름 표시
    - ProjectJpaEntity         : created_by 조인의 반대편
*/
public class MemberReferenceEntity {
}
