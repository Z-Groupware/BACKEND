package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예전에 쓰던 비밀번호 해시(V2.3.24). "쓰던 것으로 되돌리기"를 막기 위해서만 존재한다.
 *
 * <p>발급 시점에는 행이 생기지 않는다. 변경할 때 <b>직전 해시</b>를 여기 넣으므로, 첫 변경에서
 * 발급 비밀번호가 자동으로 이력이 된다 — 계정 발급·온보딩·기업 등록 어느 경로도 이 클래스를 모른다.
 *
 * <p>{@code member} 연관을 매핑하지 않고 {@code memberId} 만 들고 있다. 재사용 검사에 필요한 것은
 * 해시 문자열뿐이라 구성원 엔티티를 끌어올 이유가 없고, 끌어오면 그 순간 비밀번호 해시를 담은
 * 객체 그래프가 넓어진다.
 */
@Entity
@Table(name = "password_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PasswordHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    static PasswordHistoryJpaEntity of(Long memberId, String passwordHash, LocalDateTime at) {
        PasswordHistoryJpaEntity history = new PasswordHistoryJpaEntity();
        history.memberId = memberId;
        history.passwordHash = passwordHash;
        history.createdAt = at;
        return history;
    }
}
