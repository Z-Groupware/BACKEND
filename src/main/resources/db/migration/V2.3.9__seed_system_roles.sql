-- 시스템 역할 2개를 id 1·2 에 고정한다. 전 회사가 공유하며 회사가 만들거나 지울 수 없다.
--
--   id 1 = 리더   팀장인 사람의 역할 표시
--   id 2 = 없음   역할을 부여받지 않은 사람
--
-- "없음"을 NULL 이 아니라 실제 행으로 두는 이유 — 화면의 역할 컬럼은 빈칸이 아니라 "없음"을
-- 보여준다. NULL 로 두면 그 문구를 프론트가 만들어야 하고, 역할 목록 select 에도 "없음" 선택지를
-- 따로 끼워 넣어야 한다. 행으로 두면 목록 조회 결과가 곧 select 항목이 된다.
--
-- team_id 를 NULL 허용으로 바꾼다 — 시스템 역할은 특정 팀에 속하지 않는다.
ALTER TABLE `role`
    MODIFY COLUMN `team_id` BIGINT NULL
    COMMENT '역할이 속한 부서. NULL 이면 전 회사 공용 시스템 역할';

INSERT INTO `role` (`id`, `company_id`, `team_id`, `name`)
VALUES (1, NULL, NULL, '리더'),
       (2, NULL, NULL, '없음');

-- 회사가 만드는 역할이 1·2 를 먹지 않도록 다음 채번을 3 부터로 민다.
-- 이 줄이 없으면 첫 회사의 첫 역할이 id 3 이 되긴 하지만(위 INSERT 가 카운터를 올린다),
-- 명시해 두지 않으면 시드를 지웠다 넣는 운영 작업에서 조용히 어긋난다.
ALTER TABLE `role` AUTO_INCREMENT = 3;
