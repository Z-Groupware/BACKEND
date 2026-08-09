package com.module06.backend.calendar.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;

/* comment.
    캘린더 개인 Todo 애그리거트 루트. action과 무관한 완전히 별개 엔티티다 — 회의·AI
    파생이 아니라 사용자가 캘린더에서 직접 만드는 순수 개인용 할 일이다(2026-08-06 배분,
    Figma 확인 결과 read-only 집계가 아니라 신규 CRUD 엔티티임이 드러남).

    필드가 title·date 둘뿐인 이유 — Figma "Todo 추가" 모달 그대로. 기간 아님, 시간 없음,
    설명·우선순위 없음.

    조작 범위 = 생성·조회·완료토글뿐(2026-08-06 홍근 확인). 수정·삭제는 스코프 밖이라
    도메인 메서드도 두지 않는다 — 필요해지면 그때 추가한다(YAGNI).

    연결된 클래스
    - PersonalTodoRepository   : 저장소 계약
    - PersonalTodoJpaEntity    : 영속화 매핑 (infrastructure.persistence)
*/
@Getter
public class PersonalTodo {

    private final Long id;
    private final Long companyId;
    private final Long memberId;
    private final String title;
    private final LocalDate date;
    private boolean isDone;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private PersonalTodo(
            Long id, Long companyId, Long memberId, String title, LocalDate date,
            boolean isDone, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        this.id = id;
        this.companyId = companyId;
        this.memberId = memberId;
        this.title = title;
        this.date = date;
        this.isDone = isDone;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 생성 — 항상 미완료로 시작한다.
    public static PersonalTodo create(Long companyId, Long memberId, String title, LocalDate date) {
        return new PersonalTodo(null, companyId, memberId, title, date, false, null, null);
    }

    // 영속 계층에서 그대로 복원한다.
    public static PersonalTodo reconstitute(
            Long id, Long companyId, Long memberId, String title, LocalDate date,
            boolean isDone, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        return new PersonalTodo(id, companyId, memberId, title, date, isDone, createdAt, updatedAt);
    }

    // 체크박스 토글 — 완료면 취소로, 미완료면 완료로. 호출자가 방향을 안 골라도 되게 한다
    // (Figma: "누르면 완료, 한번 더 누르면 취소" — 홍근 확인, 2026-08-08).
    public void toggleDone() {
        this.isDone = !this.isDone;
    }
}
