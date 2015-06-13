/*
 * Copyright 2015 The Cettia Project
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

import io.cettia.platform.action.Action;
import io.cettia.platform.action.Actions;
import io.cettia.platform.action.ConcurrentActions;
import io.cettia.platform.action.VoidAction;
import io.cettia.transport.ServerTransport;
import io.cettia.transport.http.HttpTransportServer;

import java.io.IOException;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Default implementation of {@link Server}.
 * <p>
 * The following options are configurable.
 * <ul>
 * <li>{@link DefaultServer#setHeartbeat(int)}</li>
 * </ul>
 * 
 * @author Donghwan Kim
 */
public class DefaultServer implements Server {

    private Map<String, DefaultServerSocket> sockets = new ConcurrentHashMap<>();
    private Actions<ServerSocket> socketActions = new ConcurrentActions<ServerSocket>();
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
        options.put("heartbeat", "" + heartbeat);
        options.put("_heartbeat", "" + _heartbeat);
        final DefaultServerSocket socket = new DefaultServerSocket(options);
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
        return byTag(new String[] { name }, action);
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
     * A heartbeat interval in milliseconds to maintain a connection alive and
     * prevent server from holding idle connections. The default is
     * <code>20</code>s and should be larger than <code>5</code>s.
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
        String id = UUID.randomUUID().toString();

        private ObjectMapper mapper = new ObjectMapper();
        private Set<String> tags = new CopyOnWriteArraySet<>();
        private AtomicInteger eventId = new AtomicInteger();
        private ConcurrentMap<String, Actions<Object>> actionsMap = new ConcurrentHashMap<>();
        private ConcurrentMap<String, Map<String, Action<Object>>> callbacksMap = new ConcurrentHashMap<>();
        private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        private ServerTransport transport;
        private ScheduledFuture<?> deleteFuture;
        private ScheduledFuture<?> heartbeatFuture;
        private AtomicReference<State> state = new AtomicReference<>();

        public DefaultServerSocket(Map<String, String> opts) {
            this.options = opts;
            // Prepares actions for reserved events
            actionsMap.put("open", new ConcurrentActions<>());
            actionsMap.put("heartbeat", new ConcurrentActions<>());
            actionsMap.put("close", new ConcurrentActions<>());
            actionsMap.put("cache", new ConcurrentActions<>());
            actionsMap.put("error", new ConcurrentActions<>());
            // delete event should have once and memory of true
            actionsMap.put("delete", new ConcurrentActions<>(new Actions.Options().once(true).memory(true)));

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
            on("heartbeat", new VoidAction() {
                @Override
                public void on() {
                    heartbeatFuture.cancel(false);
                    heartbeatFuture = scheduleHeartbeat();
                    send("heartbeat");
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
                            actionsMap.get("delete").fire();
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
            on("reply", new Action<Map<String, Object>>() {
                @Override
                public void on(Map<String, Object> info) {
                    Map<String, Action<Object>> callbacks = callbacksMap.remove(info.get("id"));
                    Action<Object> action = (Boolean) info.get("exception") ? callbacks.get("rejected") : callbacks.get("resolved");
                    action.on(info.get("data"));
                }
            });
        }

        private ScheduledFuture<?> scheduleHeartbeat() {
            return scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    actionsMap.get("error").fire(new HeartbeatFailedException());
                    transport.close();
                }
            }, Integer.parseInt(options.get("heartbeat")), TimeUnit.MILLISECONDS);
        }

        void handshake(final ServerTransport t) {
            Action<Void> handshakeAction = new VoidAction() {
                @Override
                public void on() {
                    transport = t;
                    transport.ontext(new Action<String>() {
                        @Override
                        public void on(String text) {
                            final Map<String, Object> event = parseEvent(text);
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
                                                Map<String, Object> result = new LinkedHashMap<String, Object>();
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
                        }
                    });
                    transport.onerror(new Action<Throwable>() {
                        @Override
                        public void on(Throwable error) {
                            actionsMap.get("error").fire(error);
                        }
                    });
                    transport.onclose(new VoidAction() {
                        @Override
                        public void on() {
                            actionsMap.get("close").fire();
                        }
                    });

                    Map<String, String> headers = new LinkedHashMap<>();
                    headers.put("sid", id);
                    headers.put("heartbeat", options.get("heartbeat"));
                    headers.put("_heartbeat", options.get("_heartbeat"));
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
        public <T, U> ServerSocket send(String type, Object data, Action<T> resolved, Action<U> rejected) {
            if (state.get() != State.OPENED) {
                actionsMap.get("cache").fire(new Object[] { type, data, resolved, rejected });
            } else {
                String id = "" + eventId.incrementAndGet();
                Map<String, Object> event = new LinkedHashMap<String, Object>();
                event.put("id", id);
                event.put("type", type);
                event.put("data", data);
                event.put("reply", resolved != null || rejected != null);

                if (resolved != null || rejected != null) {
                    Map<String, Action<Object>> cbs = new LinkedHashMap<String, Action<Object>>();
                    cbs.put("resolved", (Action<Object>) resolved);
                    cbs.put("rejected", (Action<Object>) rejected);
                    callbacksMap.put(id, cbs);
                }
                String text = stringifyEvent(event);
                transport.send(text);
            }
            return this;
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
        public <T> T unwrap(Class<T> clazz) {
            return ServerTransport.class.isAssignableFrom(clazz) ? clazz.cast(transport) : null;
        }
        
        private Map<String, Object> parseEvent(String text) {
            try {
                return mapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        private String stringifyEvent(Map<String, Object> event) {
            try {
                return mapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        private static enum State {
            OPENED, CLOSED, DELETED
        }
    }

}
