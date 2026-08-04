package com.module06.backend.architecture.fixture.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** ❌ ARCH_002 위반 픽스처 — 도메인 모델이 JPA에 묶여 있다(순수 POJO 아님). */
@Entity
public class FrameworkBoundModel {

    @Id
    private Long id;

    public Long getId() {
        return id;
    }
}
