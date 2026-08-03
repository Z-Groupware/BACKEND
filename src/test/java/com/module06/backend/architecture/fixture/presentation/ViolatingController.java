package com.module06.backend.architecture.fixture.presentation;

import com.module06.backend.architecture.fixture.domain.repository.OrderRepository;

/** ❌ ARCH_001 위반 픽스처 — 컨트롤러가 리포지토리를 직접 참조한다(Service 미경유). */
public class ViolatingController {

    private final OrderRepository repository;

    public ViolatingController(OrderRepository repository) {
        this.repository = repository;
    }

    public String read(long id) {
        return repository.findById(id);
    }
}
