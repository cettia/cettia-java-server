/*
 * Copyright 2015 the original author or authors.
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

import java.io.Serializable;

/**
 * {@code Sentence} is a series of predicates that a group of socket have to follow. It makes
 * easy to write one-liner action and uses internally built actions implementing
 * {@link Serializable} that is typically needed in cluster environments. Use of {@code Sentence}
 * is preferred to that of action if the goal is the same.
 *
 * @author Donghwan Kim
 */
public class Sentence implements AbstractServerSocket<Sentence> {

  private final Server server;
  private final ServerSocketPredicate predicate;

  Sentence(Server server, ServerSocketPredicate predicate) {
    this.server = server;
    this.predicate = predicate;
  }

  /**
   * Creates and returns a sentence with a predicate. The returned sentence's predicate represents
   * a short-circuiting logical AND of the original sentence's predicate and the given predicate.
   */
  public Sentence find(ServerSocketPredicate predicate) {
    return new Sentence(server, this.predicate.and(predicate));
  }

  /**
   * Executes the given action with sockets hold by the sentence
   */
  public Sentence execute(SerializableAction<ServerSocket> action) {
    server.find(predicate, action);
    return this;
  }

  @Override
  public Sentence send(String event) {
    return send(event, null);
  }

  @Override
  public Sentence send(String event, Object data) {
    return execute(new SendAction(event, data));
  }

  @Override
  public void close() {
    execute(new CloseAction());
  }

  @Override
  public Sentence tag(String... names) {
    return execute(new TagAction(names));
  }

  @Override
  public Sentence untag(String... names) {
    return execute(new UntagAction(names));
  }

  private static class SendAction implements SerializableAction<ServerSocket> {
    private static final long serialVersionUID = 2178442626501531717L;
    private final String event;
    private final Object data;

    SendAction(String event, Object data) {
      this.event = event;
      this.data = data;
    }

    @Override
    public void on(ServerSocket socket) {
      socket.send(event, data);
    }
  }

  private static class CloseAction implements SerializableAction<ServerSocket> {
    private static final long serialVersionUID = 8154281469036373698L;

    @Override
    public void on(ServerSocket socket) {
      socket.close();
    }
  }

  private static class TagAction implements SerializableAction<ServerSocket> {
    private static final long serialVersionUID = -7789207688974771161L;
    private final String[] names;

    public TagAction(String[] names) {
      this.names = names;
    }

    @Override
    public void on(ServerSocket socket) {
      socket.tag(names);
    }
  }

  private static class UntagAction implements SerializableAction<ServerSocket> {
    private static final long serialVersionUID = -4173842573981245930L;
    private final String[] names;

    public UntagAction(String[] names) {
      this.names = names;
    }

    @Override
    public void on(ServerSocket socket) {
      socket.untag(names);
    }
  }

}
