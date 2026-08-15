-- =====================================================================
-- V2.3.23 : 부서 안 역할 이름 유일성 보장                        [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   역할 CRUD(§6-10~6-12)가 생기기 전까지 역할을 만드는 경로는 온보딩 커밋
--   한 번뿐이었고, 그래서 이름 중복은 "한 요청 안에서만" 신경 쓰면 됐다. 이제는
--   오너가 아무 때나 역할을 만들 수 있으므로, 애플리케이션의 사전 검사
--   (TeamRoleService#create 의 existsByTeamIdAndName)와 INSERT 사이에 다른 요청이
--   끼어들 수 있다. 두 요청이 모두 "없다"를 읽고 각자 INSERT 하면 같은 부서에
--   같은 이름의 역할이 둘 생긴다.
--
--   사전 검사는 친절한 조기 거절일 뿐이고, 최종 동시성 관문은 데이터베이스여야
--   한다 — V2.3.19(부서별 활성 팀장 유일성)와 같은 판단이다.
--
-- 범위를 부서(team_id)로 잡는 이유:
--   회사 단위가 아니다. 개발팀과 플랫폼팀에 각각 "백엔드"가 있는 것은 정상이고,
--   온보딩도 그렇게 받는다. team 은 한 회사에만 속하므로 부서 단위 유일성이면
--   회사 경계는 저절로 지켜진다.
--
--   시스템 역할(id 1 리더 · 2 없음, V2.3.9)은 team_id 가 NULL 이라 이 제약 밖이다 —
--   MySQL UNIQUE 는 NULL 을 서로 다른 값으로 취급한다. 둘은 애초에 이름도 다르다.
--
-- 비교 규칙:
--   name 컬럼의 콜레이션이 utf8mb4_unicode_ci 라 대소문자와 끝 공백을 무시한다.
--   아래 GROUP BY 도 같은 콜레이션을 쓰므로, 중복 정리와 제약이 같은 기준으로 돈다
--   ("Backend"와 "backend"는 여기서 같은 이름이다).
--
-- 기존 중복 정리:
--   지우기 전에 그 역할을 달고 있던 사람들을 살아남는 행으로 옮긴다. 순서가
--   뒤집히면 member.role_id 가 사라진 행을 가리킨다(role_id 는 NOT NULL 이다).
--   남길 행은 가장 작은 id — 먼저 만들어진 쪽이다. 사용자에게 두 행은 이름이
--   같아 구분되지 않으므로 어느 쪽을 남겨도 화면 결과가 같다.
--
--   파생 테이블을 한 겹 더 감싼 이유: MySQL 8 은 UPDATE/DELETE 대상 테이블을
--   FROM 절에서 직접 참조하는 것을 거부한다("You can't specify target table ...").
--   한 겹 더 감싸면 파생 테이블이 실체화되어 그 제한을 벗어난다.
--
-- 확인 쿼리(적용 후 0건이어야 한다):
--   SELECT team_id, name, COUNT(*) FROM role
--   WHERE team_id IS NOT NULL GROUP BY team_id, name HAVING COUNT(*) > 1;
-- =====================================================================

UPDATE `member` AS m
JOIN `role` AS r ON r.`id` = m.`role_id`
JOIN (
    SELECT * FROM (
        SELECT `team_id` AS team_id, `name` AS name, MIN(`id`) AS keeper_id
        FROM `role`
        WHERE `team_id` IS NOT NULL
        GROUP BY `team_id`, `name`
    ) AS grouped
) AS keep ON keep.team_id = r.`team_id` AND keep.name = r.`name`
SET m.`role_id` = keep.keeper_id
WHERE m.`role_id` <> keep.keeper_id;

DELETE r FROM `role` AS r
JOIN (
    SELECT * FROM (
        SELECT `team_id` AS team_id, `name` AS name, MIN(`id`) AS keeper_id
        FROM `role`
        WHERE `team_id` IS NOT NULL
        GROUP BY `team_id`, `name`
    ) AS grouped
) AS keep ON keep.team_id = r.`team_id` AND keep.name = r.`name`
WHERE r.`id` <> keep.keeper_id;

-- 애플리케이션은 이 제약 위반을 ROLE_NAME_DUPLICATED 로 변환한다
-- (RolePersistenceAdapter). 제약 이름을 바꾸면 그 상수도 함께 바꿔야 한다.
ALTER TABLE `role`
    ADD CONSTRAINT `UK_ROLE_TEAM_NAME` UNIQUE (`team_id`, `name`);
