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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Donghwan Kim
 */
public class DefaultServerTest {

  @Test
  public void find() {
    DefaultServer server = new DefaultServer();
    DefaultServerSocket socketA = mock(DefaultServerSocket.class);
    DefaultServerSocket socketB = mock(DefaultServerSocket.class);
    DefaultServerSocket socketC = mock(DefaultServerSocket.class);
    when(socketA.id()).thenReturn("A");
    when(socketB.id()).thenReturn("B");
    when(socketC.id()).thenReturn("C");
    server.sockets.put(socketA.id(), socketA);
    server.sockets.put(socketB.id(), socketB);
    server.sockets.put(socketC.id(), socketC);

    List<ServerSocket> result = new ArrayList<>();
    server.find(ServerSocketPredicates.all(), result::add);
    assertEquals(Arrays.asList(socketA, socketB, socketC), result);

    result.clear();
    server.find(ServerSocketPredicates.id("A"), result::add);
    assertEquals(Arrays.asList(socketA), result);

    result.clear();
    server.find(ServerSocketPredicates.id("A").negate(), result::add);
    assertEquals(Arrays.asList(socketB, socketC), result);
  }

}
