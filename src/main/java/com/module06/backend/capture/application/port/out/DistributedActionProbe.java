package com.module06.backend.capture.application.port.out;

/*
 * 이 회의가 이미 분배된 적이 있는지 본다. **재분배로 액션이 두 배가 되는 것을 막는 자리다.**
 *
 * <h2>왜 tuple 의 action_id 로는 알 수 없는가</h2>
 * ANLZ-01 강제 재실행은 tuple 을 통째로 갈아끼운다(replace). 그 순간 이전 실행이 적어 둔
 * action_id 가 함께 사라지므로, 새 tuple 들은 전부 "아직 분배되지 않은" 모양이 된다.
 * 그것만 보고 분배하면 같은 회의의 같은 배정이 액션 두 벌로 남는다 — 사람 보드에 같은 일이
 * 두 번 꽂히고, 되돌리려면 이미 알림이 나간 액션을 지워야 한다.
 *
 * 그래서 tuple 이 아니라 **action 쪽에** 이미 만들어진 것이 있는지 묻는다.
 *
 * <h2>수동 추가는 세지 않는다</h2>
 * 사람이 "+"로 만든 액션(is_manual=true)은 분석 경로의 산출물이 아니다. 그것까지 세면
 * 회의에 액션을 하나 손으로 추가한 순간 그 회의는 영영 분배되지 않는다.
 */
public interface DistributedActionProbe {

    /*
     * @return 이 회의에서 분석 경로로 만들어진 액션이 하나라도 있는가.
     *         회사 스코프를 함께 받는다 — meetingId 만으로 세면 다른 회사 회의의 분배 이력이
     *         이 회의의 판단을 바꾼다.
     */
    boolean existsForMeeting(long companyId, long meetingId);
}
