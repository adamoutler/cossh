package com.sun.net.httpserver;

import java.io.IOException;

@FunctionalInterface
public interface HttpHandler {
    void handle(HttpExchange exchange) throws IOException;
}
