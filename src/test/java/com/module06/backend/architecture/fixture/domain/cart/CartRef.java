package com.module06.backend.architecture.fixture.domain.cart;

import com.module06.backend.architecture.fixture.domain.order.OrderRef;

/** ❌ 순환 참조 위반 픽스처(cart → order). */
public class CartRef {
    private OrderRef order;

    public OrderRef order() {
        return order;
    }
}
