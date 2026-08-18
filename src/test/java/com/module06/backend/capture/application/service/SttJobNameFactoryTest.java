package com.module06.backend.capture.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 잡 이름 규칙.
 *
 * <p>검증의 축은 <b>이 이름이 AWS 계정 안에서 정말 유일한가</b>다. 이름은 meetingId(DB
 * auto-increment)로만 만들어지는데 Transcribe 의 잡 이름 네임스페이스는 계정+리전 단위고 DB 보다
 * 오래 산다 — 재시드·스냅샷 복원·환경 공유처럼 "이 DB 가 그 계정과 1:1"이 깨지는 순간 전부
 * 충돌하고, 그 회의는 제출조차 못 한 채 「요약 중」에 갇힌다.
 */
class SttJobNameFactoryTest {

    @Test
    @DisplayName("네임스페이스가 없으면 예전 형식 그대로다 — 이미 떠 있는 블록의 이름과 갈리면 안 된다")
    void 기본값은_예전_형식을_유지한다() {
        // 이름은 provider_job_name 에 저장되고 폴링이 그 값으로 조회한다. 규칙을 바꾸는 것이
        // 이미 제출된 잡을 못 찾게 만들면 안 된다.
        assertThat(new SttJobNameFactory("").create(500L, 3, 3)).isEqualTo("meeting-500-block-3-r3");
        assertThat(new SttJobNameFactory(null).create(1L, 0, 0)).isEqualTo("meeting-1-block-0-r0");
    }

    @Test
    @DisplayName("네임스페이스는 앞에 붙는다 — 재시드한 DB가 계정에 남은 옛 잡과 안 부딪히게")
    void 네임스페이스는_앞에_붙는다() {
        assertThat(new SttJobNameFactory("stg-seed7").create(1L, 0, 0))
                .isEqualTo("stg-seed7-meeting-1-block-0-r0");
    }

    @Test
    @DisplayName("같은 블록이라도 시도 횟수가 다르면 이름이 갈린다")
    void 시도_횟수가_이름을_가른다() {
        SttJobNameFactory factory = new SttJobNameFactory("stg");
        assertThat(factory.create(1L, 0, 0)).isNotEqualTo(factory.create(1L, 0, 1));
    }

    @Test
    @DisplayName("이름은 결정적이다 — 같은 입력이면 같은 이름(제공자 상태 어긋남을 감지하는 근거)")
    void 같은_입력은_같은_이름을_준다() {
        // 난수를 섞으면 충돌은 사라지지만, SttTranscribeJobAdapter 가 ConflictException 을
        // "우리 상태와 제공자 상태가 어긋났다"로 읽는 근거도 같이 사라진다.
        SttJobNameFactory factory = new SttJobNameFactory("stg");
        assertThat(factory.create(500L, 3, 3)).isEqualTo(factory.create(500L, 3, 3));
    }

    @Test
    @DisplayName("Transcribe 가 못 받는 네임스페이스는 부팅에서 막는다 — 제출 시점에 알면 늦다")
    void 허용되지_않는_문자는_부팅에서_막힌다() {
        // 여기서 안 막으면 제출 때 제공자가 거절하는데, 그때는 이미 블록이 만들어진 뒤라
        // "설정이 틀렸다"가 아니라 "STT 가 실패한다"로 보인다.
        assertThatThrownBy(() -> new SttJobNameFactory("stg 1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SttJobNameFactory("stg/1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SttJobNameFactory("한글"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("너무 긴 네임스페이스도 부팅에서 막는다 — 이름 상한(200)은 DB 컬럼 폭이기도 하다")
    void 너무_긴_네임스페이스는_부팅에서_막힌다() {
        assertThatThrownBy(() -> new SttJobNameFactory("a".repeat(65)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new SttJobNameFactory("a".repeat(64)).create(Long.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE).length()).isLessThanOrEqualTo(200);
    }

    @Test
    @DisplayName("앞뒤 공백은 다듬는다 — SSM/환경변수에서 딸려오는 개행이 이름을 깨뜨리지 않게")
    void 앞뒤_공백은_다듬는다() {
        assertThat(new SttJobNameFactory("  stg\n").create(1L, 0, 0))
                .isEqualTo("stg-meeting-1-block-0-r0");
    }
}
