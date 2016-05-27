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
import io.cettia.asity.action.VoidAction;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
public class DefaultServer implements Server {

  private static final String TAG_HEARTBEAT_2 = "_heartbeat";
  private static final String TAG_HEARTBEAT_1 = "heartbeat";

  private Map<String, DefaultServerSocket> sockets = new ConcurrentHashMap<>();
  private Actions<ServerSocket> socketActions = new ConcurrentActions<>();
  private int heartbeat = 20000;
  private int _heartbeat = 5000;

  @Override
  public void on(ServerTransport transport) {
    DefaultServerSocket socket = null;
    Map<String, String> headers = HttpTransportServer.parseQuery(transport.uri());
    String sid = headers.get("sid");
    // ConcurrentHashMap is not null-safe
    if (sid != null) {
      socket = sockets.get(sid);
    }
    if (socket == null) {
      socket = createSocket(transport);
      socketActions.fire(socket);
    }
    socket.handshake(transport);
  }

  private DefaultServerSocket createSocket(ServerTransport transport) {
    Map<String, String> options = new LinkedHashMap<>();
    options.put("TAG_HEARTBEAT_1", Integer.toString(heartbeat));
    options.put("TAG_HEARTBEAT_2", Integer.toString(_heartbeat));
    final DefaultServerSocket socket = new DefaultServerSocket(options);
    // socket.uri should be available on socket event #4
    socket.transport = transport;
    // A temporal implementation of 'once'
    final AtomicBoolean done = new AtomicBoolean();
    socket.onopen(new VoidAction() {
      @Override
      public void on() {
        if (!done.getAndSet(true)) {
          sockets.put(socket.id, socket);
          socket.ondelete(new VoidAction() {
            @Override
            public void on() {
              sockets.remove(socket.id);
            }
          });
        }
      }
    });
    return socket;
  }

  @Override
  public Sentence all() {
    return new Sentence(new Action<Action<ServerSocket>>() {
      @Override
      public void on(Action<ServerSocket> action) {
        all(action);
      }
    });
  }

  @Override
  public Server all(Action<ServerSocket> action) {
    for (ServerSocket socket : sockets.values()) {
      action.on(socket);
    }
    return this;
  }

  @Override
  public Sentence byTag(final String... names) {
    return new Sentence(new Action<Action<ServerSocket>>() {
      @Override
      public void on(Action<ServerSocket> action) {
        byTag(names, action);
      }
    });
  }

  @Override
  public Server byTag(String name, Action<ServerSocket> action) {
    return byTag(new String[]{name}, action);
  }

  @Override
  public Server byTag(String[] names, Action<ServerSocket> action) {
    List<String> nameList = Arrays.asList(names);
    for (ServerSocket socket : sockets.values()) {
      if (socket.tags().containsAll(nameList)) {
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
    private static final String TAG_REPLY = "reply";
    private static final String TAG_DELETE = "delete";
    private static final String TAG_ERROR = "error";
    private static final String TAG_CACHE = "cache";
    private static final String TAG_CLOSE = "close";
    private final Map<String, String> options;
    String id = UUID.randomUUID().toString();

    private ObjectMapper textMapper = new ObjectMapper();
    private ObjectMapper binaryMapper = new ObjectMapper(new MessagePackFactory());
    private Set<String> tags = new CopyOnWriteArraySet<>();
    private AtomicInteger eventId = new AtomicInteger();
    private ConcurrentMap<String, Actions<Object>> actionsMap = new ConcurrentHashMap<>();
    private ConcurrentMap<String, Map<String, Action<Object>>> callbacksMap = new
      ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private ServerTransport transport;
    private ScheduledFuture<?> deleteFuture;
    private ScheduledFuture<?> heartbeatFuture;
    private AtomicReference<State> state = new AtomicReference<>();

    public DefaultServerSocket(Map<String, String> opts) {
      this.options = opts;
      // Prepares actions for reserved events
      actionsMap.put("open", new ConcurrentActions<>());
      actionsMap.put(TAG_HEARTBEAT_1, new ConcurrentActions<>());
      actionsMap.put(TAG_CLOSE, new ConcurrentActions<>());
      actionsMap.put(TAG_CACHE, new ConcurrentActions<>());
      actionsMap.put(TAG_ERROR, new ConcurrentActions<>());
      // delete event should have once and memory of true
      actionsMap.put(TAG_DELETE, new ConcurrentActions<>(new Actions.Options().once(true).memory
        (true)));

      onopen(new VoidAction() {
        @Override
        public void on() {
          state.set(State.OPENED);
          heartbeatFuture = scheduleHeartbeat();
          // deleteFuture is null only on the first open event
          if (deleteFuture != null) {
            deleteFuture.cancel(false);
          }
        }
      });
      on(TAG_HEARTBEAT_1, new VoidAction() {
        @Override
        public void on() {
          heartbeatFuture.cancel(false);
          heartbeatFuture = scheduleHeartbeat();
          send(TAG_HEARTBEAT_1);
        }
      });
      onclose(new VoidAction() {
        @Override
        public void on() {
          state.set(State.CLOSED);
          heartbeatFuture.cancel(false);
          deleteFuture = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
              actionsMap.get(TAG_DELETE).fire();
            }
          }, 1, TimeUnit.MINUTES);
        }
      });
      ondelete(new VoidAction() {
        @Override
        public void on() {
          state.set(State.DELETED);
        }
      });
      on(TAG_REPLY, new Action<Map<String, Object>>() {
        @Override
        public void on(Map<String, Object> info) {
          Map<String, Action<Object>> callbacks = callbacksMap.remove(info.get("id"));
          Action<Object> action = (Boolean) info.get("exception") ? callbacks.get("rejected") :
            callbacks.get("resolved");
          action.on(info.get("data"));
        }
      });
    }

    private ScheduledFuture<?> scheduleHeartbeat() {
      return scheduler.schedule(new Runnable() {
        @Override
        public void run() {
          actionsMap.get(TAG_ERROR).fire(new HeartbeatFailedException());
          transport.close();
        }
      }, Integer.parseInt(options.get(TAG_HEARTBEAT_1)), TimeUnit.MILLISECONDS);
    }

    void handshake(final ServerTransport t) {
      Action<Void> handshakeAction = new VoidAction() {
        @Override
        public void on() {
          transport = t;
          final Action<Map<String, Object>> eventAction = new Action<Map<String, Object>>() {
            @Override
            public void on(final Map<String, Object> event) {
              Actions<Object> actions = actionsMap.get(event.get("type"));
              if (actions != null) {
                if ((Boolean) event.get(TAG_REPLY)) {
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
                        send(TAG_REPLY, result);
                      }
                    }
                  });
                } else {
                  actions.fire(event.get("data"));
                }
              }
            }
          };
          transport.ontext(new Action<String>() {
            @Override
            @SuppressWarnings({"unchecked"})
            public void on(String text) {
              try {
                eventAction.on(textMapper.readValue(text, Map.class));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          });
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
          transport.onerror(new Action<Throwable>() {
            @Override
            public void on(Throwable error) {
              actionsMap.get(TAG_ERROR).fire(error);
            }
          });
          transport.onclose(new VoidAction() {
            @Override
            public void on() {
              actionsMap.get(TAG_CLOSE).fire();
            }
          });

          Map<String, String> headers = new LinkedHashMap<>();
          headers.put("sid", id);
          headers.put(TAG_HEARTBEAT_1, options.get(TAG_HEARTBEAT_1));
          headers.put(TAG_HEARTBEAT_2, options.get(TAG_HEARTBEAT_2));
          transport.send("?" + HttpTransportServer.formatQuery(headers));
          actionsMap.get("open").fire();
        }
      };

      if (state.get() == State.OPENED) {
        transport.onclose(handshakeAction).close();
      } else {
        handshakeAction.on(null);
      }
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
      return on(TAG_CLOSE, action);
    }

    @Override
    public ServerSocket oncache(Action<Object[]> action) {
      return on(TAG_CACHE, action);
    }

    @Override
    public ServerSocket ondelete(Action<Void> action) {
      return on(TAG_DELETE, action);
    }

    @Override
    public ServerSocket onerror(Action<Throwable> action) {
      return on(TAG_ERROR, action);
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
        actionsMap.get(TAG_CACHE).fire(new Object[]{type, data, resolved, rejected});
      } else {
        String id = Integer.toString(eventId.incrementAndGet());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", id);
        event.put("type", type);
        event.put(TAG_REPLY, resolved != null || rejected != null);

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
        actionsMap.get(TAG_DELETE).fire();
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
    public <T> T unwrap(Class<T> clazz) {
      return ServerTransport.class.isAssignableFrom(clazz) ? clazz.cast(transport) : null;
    }
  }

}
