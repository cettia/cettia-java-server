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

import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Donghwan Kim
 */
public class ServerSocketPredicatesTest {

  @Test
  public void all() {
    ServerSocket socket = mock(ServerSocket.class);
    assertTrue(ServerSocketPredicates.all().test(socket));
  }

  @Test
  public void tag() {
    ServerSocket socket = mock(ServerSocket.class);
    Set<String> tags = Stream.of("hite", "max", "kloud").collect(Collectors.toSet());
    when(socket.tags()).thenReturn(tags);
    assertTrue(ServerSocketPredicates.tag("hite").test(socket));
    assertTrue(ServerSocketPredicates.tag("max", "kloud").test(socket));
    assertFalse(ServerSocketPredicates.tag("ob").test(socket));
    assertFalse(ServerSocketPredicates.tag("hite", "ob").test(socket));
  }

  @Test
  public void attr() {
    ServerSocket socket = mock(ServerSocket.class);
    when(socket.get("alcohol")).thenReturn("soju");
    assertTrue(ServerSocketPredicates.attr("alcohol", "soju").test(socket));
    assertFalse(ServerSocketPredicates.attr("alcohol", "beer").test(socket));
    assertFalse(ServerSocketPredicates.attr("ethanol", "beer").test(socket));
  }

  @Test
  public void id() {
    ServerSocket socket1 = mock(ServerSocket.class);
    when(socket1.id()).thenReturn("ID1");
    ServerSocket socket2 = mock(ServerSocket.class);
    when(socket2.id()).thenReturn("ID2");
    assertTrue(ServerSocketPredicates.id(socket1).test(socket1));
    assertTrue(ServerSocketPredicates.id("ID1").test(socket1));
    assertFalse(ServerSocketPredicates.id(socket2).test(socket1));
    assertFalse(ServerSocketPredicates.id("ID2").test(socket1));
  }

}
