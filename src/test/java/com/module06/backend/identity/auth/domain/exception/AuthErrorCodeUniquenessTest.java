package com.module06.backend.identity.auth.domain.exception;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AU 코드 문자열은 이 enum 안에서 유일해야 한다. {@code CaptureErrorCodeUniquenessTest} 와 같은 형태다.
 *
 * <p>이 테스트가 뒤늦게 생긴 이유 — <b>실제로 두 번 충돌했고 두 번 다 사람이 잡았다.</b>
 * 비밀번호 변경 네 코드는 원래 AU-040~043 이었는데 그 기능이 죽은 브랜치에 갇혀 있는 동안
 * develop 이 같은 번호를 역할 CRUD(ROLE_*)에 먼저 썼다. 비밀번호 찾기 두 코드도 똑같이
 * AU-044·045 를 잡고 있었는데 그 사이 develop 이 ONBOARDING_ROLE_NAME_DUPLICATED ·
 * PASSWORD_CONFIRM_MISMATCH 에 그 번호를 썼다. 둘 다 <b>합칠 때까지 아무도 몰랐다</b> —
 * 브랜치가 갈라져 있으면 컴파일도 통과하기 때문이다.
 *
 * <p>중복이 위험한 이유: 프론트는 코드 문자열로 문구와 분기를 고른다. 같은 코드가 두 뜻이면
 * 어느 안내를 띄울지 정할 수 없고, 그 선택은 반드시 한쪽에서 틀린다.
 *
 * <p>⚠ 이 테스트는 합류한 뒤에만 깨진다. 브랜치가 갈라져 있는 동안의 충돌은 여전히 못 잡는다 —
 * 그래서 새 코드는 develop 의 최대값 다음 번호로 잡고, 병합 시점에 이 테스트를 다시 돌린다.
 */
class AuthErrorCodeUniquenessTest {

    @Test
    @DisplayName("코드 문자열이 중복되지 않는다 — 같은 코드가 두 뜻이면 화면이 문구를 고를 수 없다")
    void 코드가_중복되지_않는다() {
        Map<String, List<String>> namesByCode = new LinkedHashMap<>();
        for (AuthErrorCode code : AuthErrorCode.values()) {
            namesByCode.computeIfAbsent(code.getCode(), key -> new ArrayList<>()).add(code.name());
        }

        Map<String, List<String>> duplicated = new LinkedHashMap<>();
        namesByCode.forEach((code, names) -> {
            if (names.size() > 1) {
                duplicated.put(code, names);
            }
        });

        assertThat(duplicated)
                .withFailMessage("중복된 에러 코드가 있습니다 — %s. "
                        + "새 코드는 빈 번호가 아니라 현재 최대값 다음 번호를 잡으세요. 이미 쓰이는 코드를 "
                        + "옮기면 그 코드로 분기하는 프론트가 조용히 깨집니다.", duplicated)
                .isEmpty();
    }

    /*
     * 폐기된 번호(AU-015·018·030·032)는 비워 둔 채로 둔다 — "코드는 공개 계약이라 다른 뜻으로
     * 재사용하지 않는다"(AU-032 주석). 그래서 번호가 연속인지는 검사하지 않는다.
     */
    @Test
    @DisplayName("코드와 메시지에 빈 값이 없다")
    void 코드가_비어_있지_않다() {
        for (AuthErrorCode code : AuthErrorCode.values()) {
            assertThat(code.getCode())
                    .withFailMessage("%s 의 코드가 비어 있습니다.", code.name())
                    .isNotBlank();
            assertThat(code.getMessage())
                    .withFailMessage("%s 의 메시지가 비어 있습니다.", code.name())
                    .isNotBlank();
            assertThat(code.getHttpStatus())
                    .withFailMessage("%s 의 HTTP 상태가 비어 있습니다.", code.name())
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("모든 코드가 AU- 접두어를 쓴다 — 도메인 카탈로그가 섞이면 프론트가 두 곳을 본다")
    void 접두어가_AU다() {
        for (AuthErrorCode code : AuthErrorCode.values()) {
            assertThat(code.getCode())
                    .withFailMessage("%s 의 코드(%s)가 AU- 로 시작하지 않습니다.", code.name(), code.getCode())
                    .startsWith("AU-");
        }
    }
}
