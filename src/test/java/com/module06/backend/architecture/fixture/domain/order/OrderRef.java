package com.module06.backend.architecture.fixture.domain.order;

import com.module06.backend.architecture.fixture.domain.cart.CartRef;

/** ❌ 순환 참조 위반 픽스처(order → cart). */
public class OrderRef {
    private CartRef cart;

    public CartRef cart() {
        return cart;
    }
}
