package com.module06.backend.architecture.fixture.domain.repository;

/** 픽스처 — presentation이 직접 부르면 안 되는 리포지토리 역할. */
public interface OrderRepository {
    String findById(long id);
}
