-- job_position → position. 화면에서 이 테이블을 "직급"이라고 부른다.
--
-- ⚠️ position 은 MySQL·H2 양쪽에서 키워드다(POSITION() 함수). 이 테이블을 참조하는 모든 DDL·DML 은
--    백틱으로 감싸야 하고, JPA 쪽도 @Table(name = "`position`") 로 인용해야 한다.
--    인용을 빠뜨리면 파싱 단계에서 죽으므로 조용히 넘어가지는 않는다.
RENAME TABLE `job_position` TO `position`;

ALTER TABLE `position`
    DROP PRIMARY KEY,
    ADD CONSTRAINT `PK_POSITION` PRIMARY KEY (`id`);
