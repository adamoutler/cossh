package com.sun.net.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public abstract class HttpExchange implements AutoCloseable {
    protected HttpExchange() {}
    public abstract Headers getRequestHeaders();
    public abstract Headers getResponseHeaders();
    public abstract URI getRequestURI();
    public abstract String getRequestMethod();
    public abstract InputStream getRequestBody();
    public abstract OutputStream getResponseBody();
    public abstract void sendResponseHeaders(int rCode, long responseLength) throws IOException;
    public abstract void close();
}
