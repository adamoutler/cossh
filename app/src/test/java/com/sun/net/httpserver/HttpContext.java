package com.sun.net.httpserver;

public abstract class HttpContext {
    protected HttpContext() {}
    public abstract String getPath();
    public abstract HttpHandler getHandler();
}
