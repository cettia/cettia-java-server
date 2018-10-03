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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Donghwan Kim
 */
public class SentenceTest {

  ServerSocketPredicate NOOP_PREDICATE = socket -> true;
  SerializableAction<ServerSocket> NOOP_ACTION = socket -> socket.id();

  @Test
  public void execute() {
    Server server = new DefaultServer() {
      @Override
      public Server find(ServerSocketPredicate predicate, SerializableAction<ServerSocket> action) {
        assertSame(NOOP_PREDICATE, predicate);
        assertSame(NOOP_ACTION, action);
        return this;
      }
    };
    server.find(NOOP_PREDICATE).execute(NOOP_ACTION);
  }

  @Test
  public void find() {
    ServerSocket socket = mock(ServerSocket.class);
    Server server = new DefaultServer() {
      @Override
      public Server find(ServerSocketPredicate predicate, SerializableAction<ServerSocket> action) {
        when(socket.get("stop")).thenReturn("doing");
        when(socket.get("start")).thenReturn("talking");
        assertFalse(predicate.test(socket));

        when(socket.get("stop")).thenReturn("talking");
        when(socket.get("start")).thenReturn("talking");
        assertFalse(predicate.test(socket));

        when(socket.get("stop")).thenReturn("talking");
        when(socket.get("start")).thenReturn("doing");
        assertTrue(predicate.test(socket));
        return this;
      }
    };

    Sentence s1 = server.find(ServerSocketPredicates.attr("stop", "talking"));
    Sentence s2 = s1.find(ServerSocketPredicates.attr("start", "doing"));

    assertNotSame(s1, s2);
    s2.execute(NOOP_ACTION);
  }

  @Test
  public void send() {
    ServerSocket socket = mock(ServerSocket.class);
    Server server = new DefaultServer() {
      @Override
      public Server find(ServerSocketPredicate predicate, SerializableAction<ServerSocket> action) {
        action.on(socket);
        return this;
      }
    };
    server.find(NOOP_PREDICATE).send("event");
    verify(socket, times(1)).send("event", null);
    server.find(NOOP_PREDICATE).send("event", "data");
    verify(socket, times(1)).send("event", "data");
  }

  @Test
  public void close() {
    ServerSocket socket = mock(ServerSocket.class);
    Server server = new DefaultServer() {
      @Override
      public Server find(ServerSocketPredicate predicate, SerializableAction<ServerSocket> action) {
        action.on(socket);
        return this;
      }
    };
    server.find(NOOP_PREDICATE).close();
    verify(socket, times(1)).close();
  }

  @Test
  public void tag() {
    ServerSocket socket = mock(ServerSocket.class);
    Server server = new DefaultServer() {
      @Override
      public Server find(ServerSocketPredicate predicate, SerializableAction<ServerSocket> action) {
        action.on(socket);
        return this;
      }
    };
    server.find(NOOP_PREDICATE).tag("active");
    verify(socket, times(1)).tag("active");
  }

  @Test
  public void untag() {
    ServerSocket socket = mock(ServerSocket.class);
    Server server = new DefaultServer() {
      @Override
      public Server find(ServerSocketPredicate predicate, SerializableAction<ServerSocket> action) {
        action.on(socket);
        return this;
      }
    };
    server.find(NOOP_PREDICATE).untag("active");
    verify(socket, times(1)).untag("active");
  }

}
