package com.sun.net.httpserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

public abstract class HttpServer {
    protected HttpServer() {}

    public static HttpServer create(InetSocketAddress addr, int backlog) throws IOException {
        throw new UnsupportedOperationException("Compile stub");
    }

    public abstract void start();
    public abstract void setExecutor(Executor executor);
    public abstract Executor getExecutor();
    public abstract void stop(int delay);
    public abstract HttpContext createContext(String path, HttpHandler handler);
    public abstract InetSocketAddress getAddress();
}
