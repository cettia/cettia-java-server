/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cettia;

import io.cettia.DefaultServer.DefaultServerSocket;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Donghwan Kim
 */
public class ServerSocketPredicateTest {

  @Test
  public void and() {
    ServerSocketPredicate predicate1 = socket -> true;
    ServerSocketPredicate predicate2 = socket -> false;
    ServerSocketPredicate predicate3 = socket -> true;

    DefaultServerSocket socket = mock(DefaultServerSocket.class);
    assertFalse(predicate1.and(predicate2).test(socket));
    assertFalse(predicate2.and(predicate1).test(socket));
    assertTrue(predicate1.and(predicate3).test(socket));
    assertTrue(predicate3.and(predicate1).test(socket));
  }

  @Test
  public void or() {
    ServerSocketPredicate predicate1 = socket -> true;
    ServerSocketPredicate predicate2 = socket -> false;
    ServerSocketPredicate predicate3 = socket -> false;

    DefaultServerSocket socket = mock(DefaultServerSocket.class);
    assertTrue(predicate1.or(predicate2).test(socket));
    assertTrue(predicate2.or(predicate1).test(socket));
    assertFalse(predicate2.or(predicate3).test(socket));
    assertFalse(predicate3.or(predicate2).test(socket));
  }

  @Test
  public void negate() {
    ServerSocketPredicate predicate = socket -> true;
    DefaultServerSocket socket = mock(DefaultServerSocket.class);
    assertFalse(predicate.negate().test(socket));
  }

}
