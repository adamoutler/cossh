package com.sun.net.httpserver;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Headers extends HashMap<String, List<String>> {
    public void set(String key, String value) {
        List<String> l = new LinkedList<>();
        l.add(value);
        put(key, l);
    }

    public void add(String key, String value) {
        computeIfAbsent(key, k -> new LinkedList<>()).add(value);
    }

    public String getFirst(String key) {
        for (Map.Entry<String, List<String>> entry : entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                List<String> list = entry.getValue();
                if (list != null && !list.isEmpty()) {
                    return list.get(0);
                }
            }
        }
        return null;
    }
}
