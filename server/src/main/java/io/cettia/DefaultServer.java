/*
 * Copyright 2017 the original author or authors.
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ByteArraySerializer;
import com.fasterxml.jackson.databind.ser.std.ByteBufferSerializer;
import io.cettia.asity.action.Action;
import io.cettia.asity.action.Actions;
import io.cettia.asity.action.ConcurrentActions;
import io.cettia.transport.ServerTransport;
import io.cettia.transport.http.HttpTransportServer;
import org.msgpack.jackson.dataformat.MessagePackExtensionType;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link Server}.
 * <p/>
 * The following options are configurable.
 * <ul>
 * <li>{@link DefaultServer#setHeartbeat(int)}</li>
 * </ul>
 *
 * @author Donghwan Kim
 */
// TODO Extract constants
public class DefaultServer implements Server {

  private Map<String, DefaultServerSocket> sockets = new ConcurrentHashMap<>();
  private Actions<ServerSocket> socketActions = new ConcurrentActions<>();
  private int heartbeat = 20000;
  private int _heartbeat = 5000;

  // This thread will be used for scheduling all heartbeats and related messaging
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
    AtomicInteger threadId = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable r) {
      return new Thread(r, "Cettia-Scheduler-" + threadId.getAndIncrement());
    }
  });

  // This pool will be used to actually dispatch the events to connected clients
  private final Executor workers = Executors.newCachedThreadPool(new ThreadFactory() {
    AtomicInteger threadId = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable r) {
      return new Thread(r, "Cettia-Worker-" + threadId.getAndIncrement());
    }
  });

  @Override
  public void on(ServerTransport transport) {
    DefaultServerSocket socket = null;
    Map<String, String> headers = HttpTransportServer.parseQuery(transport.uri());
    String socketId = headers.get("cettia-id");
    // ConcurrentHashMap is not null-safe
    if (socketId != null) {
      socket = sockets.get(socketId);
    }
    if (socket == null) {
      socket = createSocket(transport);
      socketActions.fire(socket);
    }
    socket.handshake(transport);
  }

  private DefaultServerSocket createSocket(ServerTransport transport) {
    Map<String, String> options = new LinkedHashMap<>();
    options.put("heartbeat", Integer.toString(heartbeat));
    options.put("_heartbeat", Integer.toString(_heartbeat));
    final DefaultServerSocket socket = new DefaultServerSocket(options, scheduler, workers);
    // socket.uri should be available on socket event #4
    socket.transport = transport;
    // A temporal implementation of 'once'
    final AtomicBoolean done = new AtomicBoolean();
    socket.onopen($ -> {
      if (!done.getAndSet(true)) {
        sockets.put(socket.id(), socket);
        socket.ondelete($1 -> sockets.remove(socket.id()));
      }
    });
    return socket;
  }

  @Override
  public Sentence find(ServerSocketPredicate predicate) {
    return new Sentence(this, predicate);
  }

  @Override
  public Server find(ServerSocketPredicate predicate, SerializableAction<ServerSocket> action) {
    for (ServerSocket socket : sockets.values()) {
      if (predicate.test(socket)) {
        action.on(socket);
      }
    }
    return this;
  }

  @Override
  public Server onsocket(Action<ServerSocket> action) {
    socketActions.add(action);
    return this;
  }

  /**
   * A heartbeat interval in milliseconds to maintain a connection alive and prevent server from
   * holding idle connections. The default is <code>20</code>s and should be larger than
   * <code>5</code>s.
   */
  public void setHeartbeat(int heartbeat) {
    this.heartbeat = heartbeat;
  }

  /**
   * To speed up the protocol tests. Not for production use.
   */
  public void set_heartbeat(int _heartbeat) {
    this._heartbeat = _heartbeat;
  }

  private static class DefaultServerSocket implements ServerSocket {
    private final Map<String, String> options;
    private final String id = UUID.randomUUID().toString();
    private final Set<String> tags = new CopyOnWriteArraySet<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    private ObjectMapper textMapper = new ObjectMapper();
    private ObjectMapper binaryMapper = new ObjectMapper(new MessagePackFactory());
    private AtomicInteger eventId = new AtomicInteger();
    private ConcurrentMap<String, Actions<Object>> actionsMap = new ConcurrentHashMap<>();
    private ConcurrentMap<String, Map<String, Action<Object>>> callbacksMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Executor workers;

    private ServerTransport transport;
    private ScheduledFuture<?> deleteFuture;
    private ScheduledFuture<?> heartbeatFuture;
    private AtomicReference<State> state = new AtomicReference<>();

    public DefaultServerSocket(Map<String, String> opts, final ScheduledExecutorService scheduler, final Executor workers) {
      this.options = opts;

      this.scheduler = scheduler;
      this.workers = workers;

      // Prepares actions for reserved events
      actionsMap.put("open", new ConcurrentActions<>());
      actionsMap.put("heartbeat", new ConcurrentActions<>());
      actionsMap.put("close", new ConcurrentActions<>());
      actionsMap.put("cache", new ConcurrentActions<>());
      actionsMap.put("error", new ConcurrentActions<>());
      // delete event should have once and memory of true
      actionsMap.put("delete", new ConcurrentActions<>(new Actions.Options().once(true).memory
        (true)));

      onopen($ -> {
        state.set(State.OPENED);
        heartbeatFuture = scheduleHeartbeat();
        // deleteFuture is null only on the first open event
        if (deleteFuture != null) {
          deleteFuture.cancel(false);
        }
      });
      on("heartbeat", $ -> {
        heartbeatFuture.cancel(false);
        heartbeatFuture = scheduleHeartbeat();
        send("heartbeat");
      });
      onclose($ -> {
        state.set(State.CLOSED);
        heartbeatFuture.cancel(false);
        // Schedule delete for 1 min in the future, but do the work on a separate thread
        deleteFuture = scheduler.schedule(() -> {
          // Here we pass the actual work off to a worker thread so the scheduler isn't blocked
          workers.execute(() -> actionsMap.get("delete").fire());
        }, 1, TimeUnit.MINUTES);
      });
      ondelete($ -> state.set(State.DELETED));
      on("reply", (Map<String, Object> info) -> {
        Map<String, Action<Object>> callbacks = callbacksMap.remove(info.get("id"));
        Action<Object> action = (Boolean) info.get("exception") ? callbacks.get("rejected") :
          callbacks.get("resolved");
        action.on(info.get("data"));
      });
    }

    private ScheduledFuture<?> scheduleHeartbeat() {
      // Schedule heartbeat event for the future, but do work on a separate thread
      return scheduler.schedule(() -> {
        // Here we pass the actual work off to a worker thread so the scheduler isn't blocked
        workers.execute(() -> {
          actionsMap.get("error").fire(new HeartbeatFailedException());
          transport.close();
        });
      }, Integer.parseInt(options.get("heartbeat")), TimeUnit.MILLISECONDS);
    }

    void handshake(final ServerTransport t) {
      Action<Void> handshakeAction = $ -> {
        transport = t;
        final Action<Map<String, Object>> eventAction = event -> {
          Actions<Object> actions = actionsMap.get(event.get("type"));
          if (actions != null) {
            if ((Boolean) event.get("reply")) {
              final AtomicBoolean sent = new AtomicBoolean();
              actions.fire(new Reply<Object>() {
                @Override
                public Object data() {
                  return event.get("data");
                }

                @Override
                public void resolve() {
                  resolve(null);
                }

                @Override
                public void resolve(Object value) {
                  sendReply(value, false);
                }

                @Override
                public void reject() {
                  reject(null);
                }

                @Override
                public void reject(Object value) {
                  sendReply(value, true);
                }

                private void sendReply(Object value, boolean exception) {
                  if (sent.compareAndSet(false, true)) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", event.get("id"));
                    result.put("data", value);
                    result.put("exception", exception);
                    send("reply", result);
                  }
                }
              });
            } else {
              actions.fire(event.get("data"));
            }
          }
        };
        transport.ontext(text -> {
          try {
            eventAction.on(textMapper.readValue(text, Map.class));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
        // FIXME convert it to lambda
        transport.onbinary(new Action<ByteBuffer>() {
          @Override
          @SuppressWarnings({"unchecked"})
          public void on(ByteBuffer binary) {
            byte[] bytes = new byte[binary.remaining()];
            binary.get(bytes);
            try {
              Map<String, Object> event = binaryMapper.readValue(bytes, Map.class);
              event.put("data", replace(event.get("data")));
              eventAction.on(event);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }

          // Only valid for value read by Jackson
          @SuppressWarnings({"unchecked"})
          private Object replace(Object value) {
            if (value instanceof Map) {
              Map<String, Object> map = (Map) value;
              for (Map.Entry entry : map.entrySet()) {
                entry.setValue(replace(entry.getValue()));
              }
            } else if (value instanceof List) {
              List<Object> list = (List) value;
              for (int i = 0; i < list.size(); i++) {
                list.set(i, replace(list.get(i)));
              }
            } else if (value instanceof MessagePackExtensionType) {
              MessagePackExtensionType ext = (MessagePackExtensionType) value;
              byte type = ext.getType();
              // msgpack-lite that is one of dependencies of cettia-javascript-client encodes
              // typed arrays to ext format but it's not meaningful in other languages
              // Regards them as bin format
              // See https://github.com/kawanet/msgpack-lite#extension-types
              if (0x11 <= type && type != 0x1B && type != 0x1C && type <= 0x1D) {
                value = ext.getData();
              }
            }
            return value;
          }
        });
        transport.onerror(error -> actionsMap.get("error").fire(error));
        transport.onclose($1 -> actionsMap.get("close").fire());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("cettia-version", "1.0");
        headers.put("cettia-id", id);
        headers.put("cettia-heartbeat", options.get("heartbeat"));
        headers.put("cettia-_heartbeat", options.get("_heartbeat"));
        transport.send("?" + HttpTransportServer.formatQuery(headers));
        actionsMap.get("open").fire();
      };

      if (state.get() == State.OPENED) {
        transport.onclose(handshakeAction).close();
      } else {
        handshakeAction.on(null);
      }
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public State state() {
      return state.get();
    }

    @Override
    public String uri() {
      return transport.uri();
    }

    @Override
    public Set<String> tags() {
      return tags;
    }

    @Override
    public Map<String, Object> attributes() {
      return attributes;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ServerSocket on(String event, Action<T> action) {
      Actions<Object> actions = actionsMap.get(event);
      if (actions == null) {
        Actions<Object> value = new ConcurrentActions<>();
        actions = actionsMap.putIfAbsent(event, value);
        if (actions == null) {
          actions = value;
        }
      }
      actions.add((Action<Object>) action);
      return this;
    }

    @Override
    public ServerSocket onopen(Action<Void> action) {
      return on("open", action);
    }

    @Override
    public ServerSocket onclose(Action<Void> action) {
      return on("close", action);
    }

    @Override
    public ServerSocket oncache(Action<Object[]> action) {
      return on("cache", action);
    }

    @Override
    public ServerSocket ondelete(Action<Void> action) {
      return on("delete", action);
    }

    @Override
    public ServerSocket onerror(Action<Throwable> action) {
      return on("error", action);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ServerSocket off(String event, Action<T> action) {
      Actions<Object> actions = actionsMap.get(event);
      if (actions != null) {
        actions.remove((Action<Object>) action);
      }
      return this;
    }

    @Override
    public ServerSocket send(String event) {
      return send(event, null);
    }

    @Override
    public ServerSocket send(String event, Object data) {
      return send(event, data, null);
    }

    @Override
    public <T> ServerSocket send(String type, Object data, Action<T> resolved) {
      return send(type, data, resolved, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, U> ServerSocket send(String type, Object data, Action<T> resolved, Action<U>
      rejected) {
      if (state.get() != State.OPENED) {
        actionsMap.get("cache").fire(new Object[]{type, data, resolved, rejected});
      } else {
        String id = Integer.toString(eventId.incrementAndGet());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", id);
        event.put("type", type);
        event.put("reply", resolved != null || rejected != null);

        if (resolved != null || rejected != null) {
          Map<String, Action<Object>> cbs = new LinkedHashMap<>();
          cbs.put("resolved", (Action<Object>) resolved);
          cbs.put("rejected", (Action<Object>) rejected);
          callbacksMap.put(id, cbs);
        }

        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        final BooleanHolder containsBinary = new BooleanHolder();
        module.addSerializer(byte[].class, new ByteArraySerializer() {
          @Override
          public void serialize(byte[] bytes, JsonGenerator gen, SerializerProvider provider) throws
            IOException {
            containsBinary.set(true);
            super.serialize(bytes, gen, provider);
          }
        });
        module.addSerializer(ByteBuffer.class, new ByteBufferSerializer() {
          @Override
          public void serialize(ByteBuffer bytes, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
            containsBinary.set(true);
            super.serialize(bytes, gen, provider);
          }
        });
        mapper.registerModule(module);
        event.put("data", mapper.convertValue(data, Object.class));

        if (containsBinary.get()) {
          try {
            transport.send(ByteBuffer.wrap(binaryMapper.writeValueAsBytes(event)));
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        } else {
          try {
            transport.send(textMapper.writeValueAsString(event));
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        }
      }
      return this;
    }

    private static class BooleanHolder {
      private boolean val;

      public boolean get() {
        return val;
      }

      public void set(boolean val) {
        this.val = val;
      }
    }

    @Override
    public void close() {
      if (state.get() == State.OPENED) {
        transport.close();
      } else {
        if (deleteFuture != null) {
          deleteFuture.cancel(false);
        }
        actionsMap.get("delete").fire();
      }
    }

    @Override
    public ServerSocket tag(String... names) {
      tags.addAll(Arrays.asList(names));
      return this;
    }

    @Override
    public ServerSocket untag(String... names) {
      tags.removeAll(Arrays.asList(names));
      return this;
    }

    @Override
    public <T> T get(String name) {
      return (T) attributes.get(name);
    }

    @Override
    public ServerSocket set(String name, Object value) {
      attributes.put(name, value);
      return this;
    }

    @Override
    public ServerSocket remove(String name) {
      attributes.remove(name);
      return this;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
      return ServerTransport.class.isAssignableFrom(clazz) ? clazz.cast(transport) : null;
    }

    @Override
    public String toString() {
      return String.format("ServerSocket@%s[state=%s,tags=%s,attributes=%s]", id, state, tags, attributes);
    }
  }

}
