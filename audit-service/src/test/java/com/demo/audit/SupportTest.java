package com.demo.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.demo.audit.common.Support;
import org.junit.jupiter.api.Test;

class SupportTest {
  @Test
  void sha256IsDeterministic() {
    assertEquals(Support.hash("abc"), Support.hash("abc"));
    assertEquals(64, Support.hash("abc").length());
  }
}
