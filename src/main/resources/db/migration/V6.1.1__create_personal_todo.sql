-- =====================================================================
-- V6.1.1 : personal_todo — 캘린더 개인 Todo (김민섭 레인 V6.x)
-- ---------------------------------------------------------------------
-- 2026-08-06 배분된 "캘린더" 작업이 Figma 확인 결과 read-only 집계가 아니라
-- 신규 CRUD 엔티티임이 드러났다 — 회의·AI 파생이 아닌, 사용자가 캘린더에서
-- 직접 만드는 순수 개인용 할 일이다.
--
-- 필드가 딱 둘(title·date)인 이유 — Figma "Todo 추가" 모달을 그대로 반영한다.
-- 기간(시작~종료) 아님, 시간 필드 없음, 설명·우선순위 없음. 하루 단위 마감만 있다.
--
-- 조작 범위가 생성·조회·완료처리뿐인 이유 — 2026-08-06 홍근 확인: "일단은 CR만
-- 있고, 완료처리하는 게 있다." 수정·삭제 API는 스코프 밖(기술적으로는 U/D도
-- 어렵지 않지만, 화면 근거가 없는 엔드포인트를 미리 만들지 않는다는 원칙).
--
-- company_id를 넣는 이유 — action 테이블과 동일하게 테넌트 스코프용 의도적
-- 반정규화. member_id만으로도 결과적으로 회사가 갈리지만, 조회 조건에 항상
-- 넣어야 한다는 원칙을 여기서도 따른다(existsMemberInCompany 사건 참고).
-- =====================================================================

CREATE TABLE `personal_todo` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `company_id` BIGINT       NOT NULL COMMENT '테넌트 스코프용 의도적 반정규화',
    `member_id`  BIGINT       NOT NULL COMMENT '이 Todo를 만든 개인 (조회·완료 모두 본인 소유분만)',
    `title`      VARCHAR(200) NOT NULL COMMENT 'Figma placeholder "Todo 내용"',
    `date`       DATE         NOT NULL COMMENT '단일 마감일. 기간·시간 없음',
    `is_done`    BOOLEAN      NOT NULL DEFAULT FALSE,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `IX_PERSONAL_TODO_MEMBER_DATE` (`member_id`, `date`) COMMENT '캘린더 월별 조회 스캔 경로'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
