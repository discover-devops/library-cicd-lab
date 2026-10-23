package com.discoverdevops.library;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InfoController {

    private final String version;

    public InfoController(@Value("${app.version}") String version) {
        this.version = version;
    }

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("message", "Discover DevOps Library API is running", "docs", "Try /api/books");
    }

    /** Shows which commit is running and which pod answered, handy after a deployment. */
    @GetMapping("/api/info")
    public Map<String, String> info() {
        return Map.of(
                "app", "library-api",
                "version", version,
                "host", System.getenv().getOrDefault("HOSTNAME", "unknown"));
    }
}
