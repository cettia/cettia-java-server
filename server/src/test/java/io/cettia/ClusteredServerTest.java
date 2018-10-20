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
import io.cettia.asity.action.Action;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static io.cettia.ServerSocketPredicates.all;
import static io.cettia.ServerSocketPredicates.id;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Donghwan Kim
 */
public class ClusteredServerTest {

  final ServerSocketPredicate NOOP_PREDICATE = socket -> true;
  final SerializableAction<ServerSocket> NOOP_ACTION = ServerSocket::id;

  @Test
  public void onpublish() {
    ClusteredServer server = new ClusteredServer();
    server.onpublish(map -> {
      assertEquals(NOOP_PREDICATE, map.get("predicate"));
      assertEquals(NOOP_ACTION, map.get("action"));
    });
    server.find(NOOP_PREDICATE, NOOP_ACTION);
  }

  @Test
  public void messageAction() {
    Set<ClusteredServer> servers = new LinkedHashSet<>();

    ClusteredServer server1 = new ClusteredServer();
    DefaultServerSocket socketA = mock(DefaultServerSocket.class);
    DefaultServerSocket socketB = mock(DefaultServerSocket.class);
    when(socketA.id()).thenReturn("A");
    when(socketB.id()).thenReturn("B");
    server1.sockets.put(socketA.id(), socketA);
    server1.sockets.put(socketB.id(), socketB);
    servers.add(server1);

    ClusteredServer server2 = new ClusteredServer();
    DefaultServerSocket socketC = mock(DefaultServerSocket.class);
    DefaultServerSocket socketD = mock(DefaultServerSocket.class);
    when(socketC.id()).thenReturn("C");
    when(socketD.id()).thenReturn("D");
    server2.sockets.put(socketC.id(), socketC);
    server2.sockets.put(socketD.id(), socketD);
    servers.add(server2);

    Action<Map<String, Object>> pubAction = map -> servers.forEach(server -> server.messageAction().on(map));
    servers.forEach(server -> server.onpublish(pubAction));

    Set<String> ids = new LinkedHashSet<>();
    server1.find(all(), socket -> ids.add(socket.id()));
    assertEquals(new LinkedHashSet<>(Arrays.asList("A", "B", "C", "D")), ids);

    ids.clear();
    server2.find(id("A"), socket -> ids.add(socket.id()));
    assertEquals(new LinkedHashSet<>(Arrays.asList("A")), ids);
  }

}
