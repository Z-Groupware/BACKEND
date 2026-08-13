package com.module06.backend.identity.member.application.usecase;

import com.module06.backend.identity.member.application.command.DeleteMemberCommand;

/**
 * §7 사원 삭제. 소프트 삭제만 있다 — 물리 삭제 경로는 이 도메인 어디에도 두지 않는다.
 *
 * <p>인수인계 도메인의 오프보딩({@code MemberStatusPort#offboard})과 다른 사건이다. 그쪽은
 * 인수인계 최종 승인의 결과이고, 이쪽은 관리자가 목록에서 바로 닫는 경로다.
 */
public interface DeleteMemberUseCase {

    void delete(DeleteMemberCommand command);
}
