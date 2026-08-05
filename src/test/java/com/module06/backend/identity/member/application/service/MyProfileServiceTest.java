package com.module06.backend.identity.member.application.service;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.application.dto.MyProfile;
import com.module06.backend.identity.member.application.port.out.MyProfileQueryPort;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.Plan;
import com.module06.backend.identity.member.domain.model.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MyProfileService")
class MyProfileServiceTest {

    @Test
    @DisplayName("memberId 로 프로필을 돌려준다")
    void returnsProfile() {
        MyProfileService service = new MyProfileService(memberId -> Optional.of(profile()));

        assertThat(service.get(3L).name()).isEqualTo("이하윤");
    }

    @Test
    @DisplayName("없는 회원은 MEMBER_NOT_FOUND — 토큰은 유효한데 회원이 지워진 경우다")
    void missingMemberThrows() {
        MyProfileService service = new MyProfileService(memberId -> Optional.empty());

        assertThatThrownBy(() -> service.get(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("받은 memberId 를 그대로 조회에 넘긴다 — 남의 프로필을 내려주면 정보 유출이다")
    void passesRequestedMemberIdThrough() {
        RecordingPort port = new RecordingPort();
        new MyProfileService(port).get(42L);

        assertThat(port.requestedMemberId).isEqualTo(42L);
    }

    private static MyProfile profile() {
        return new MyProfile(
                3L, 1L, "(주)테크스타트", "8AS2-G8T1",
                "이하윤", "hayun@zgroup.co.kr", "010-1000-0003",
                2L, "개발팀", "프론트엔드", 4L, "선임",
                Role.MEMBER, false, true,
                MemberStatus.ACTIVE, LocalDate.of(2022, 5, 10), Plan.FREE);
    }

    private static final class RecordingPort implements MyProfileQueryPort {
        private Long requestedMemberId;

        @Override
        public Optional<MyProfile> findByMemberId(Long memberId) {
            this.requestedMemberId = memberId;
            return Optional.of(profile());
        }
    }
}
