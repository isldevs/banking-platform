package com.demo.customer;

import com.demo.customer.common.Support;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportTest {
    @Test
    void sha256IsDeterministic() {
        assertEquals(Support.hash("abc"), Support.hash("abc"));
        assertEquals(64, Support.hash("abc").length());
    }
}
