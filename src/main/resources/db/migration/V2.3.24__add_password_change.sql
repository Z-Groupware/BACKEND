-- =====================================================================
-- V2.3.24 : 비밀번호 변경(마이페이지) — password_changed_at + password_history
-- ---------------------------------------------------------------------
-- 지금까지 password_hash 는 발급 시 1회만 채워지고 바뀌지 않았다. 마이페이지
-- 셀프 변경(PATCH /api/auth/me/password)이 생기면서 두 가지가 필요해졌다.
--
-- 1) member.password_changed_at
--    "아직 발급받은 비밀번호를 그대로 쓰는 사람"을 서버가 알아야 한다. /me 응답의
--    passwordChanged 로 나가고, 프론트가 최초 1회 안내 배너를 띄우는 근거다.
--    강제 변경이 아니다 — 안 바꿔도 서비스는 그대로 쓴다. NULL 이 "한 번도 안 바꿈"
--    이고, 기존 계정은 전부 NULL 로 시작하는 것이 정확한 사실이다(백필 없음).
--
-- 2) password_history
--    "예전에 쓰던 비밀번호로 되돌리기"를 막는다. 현재 해시 하나만 비교하면 두 번째
--    변경부터 첫 번째 값으로 돌아갈 수 있다.
--
--    발급 시점에는 아무것도 넣지 않는다. 변경할 때 "직전 해시"를 여기에 밀어 넣으면
--    첫 변경에서 발급 비밀번호가 자동으로 이력이 된다 — 계정 발급(§5-1)·온보딩(§4-1)·
--    기업 등록(API 27) 세 경로를 하나도 건드리지 않아도 된다.
--
--    행을 지우지 않는다(상한 없음). 검증이 쌓인 행 수만큼 BCrypt 를 돌리므로 아주
--    자주 바꾸는 계정은 응답이 느려질 수 있다 — 실제로 문제가 되면 최근 N개만 보도록
--    조회를 자르면 되고, 그때 이 주석을 함께 고친다.
--
-- member_id 에 FK 를 걸지 않는다. personal_todo(V6.1.1)와 같은 규칙이고, 구성원은
-- 물리 삭제되지 않아(soft delete) 고아 행이 생기지 않는다.
--
-- company_id 를 함께 넣는 이유 — personal_todo(V6.1.1)·action 과 같은 테넌트 스코프용 의도적
-- 반정규화다. member_id 만으로도 결과적으로 회사가 갈리지만(구성원은 회사 하나에만 속한다),
-- "조회 조건에 회사를 항상 넣는다"는 원칙을 여기서도 따른다. Gate 1 의 TENANT_001 이 이 원칙을
-- 강제한다.
-- =====================================================================

ALTER TABLE `member`
    ADD COLUMN `password_changed_at` DATETIME NULL
        COMMENT '마지막 비밀번호 변경 시각. NULL 이면 발급받은 비밀번호를 그대로 쓰는 중'
        AFTER `password_hash`;

CREATE TABLE `password_history` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `company_id`    BIGINT       NOT NULL COMMENT '테넌트 스코프용 의도적 반정규화',
    `member_id`     BIGINT       NOT NULL COMMENT '이 해시를 쓰던 구성원',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 해시. 평문은 어떤 경로로도 저장하지 않는다',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '이 해시가 이력으로 밀려난 시각',
    PRIMARY KEY (`id`),
    KEY `IX_PASSWORD_HISTORY_MEMBER` (`member_id`, `company_id`) COMMENT '변경 시 재사용 검사 스캔 경로'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
