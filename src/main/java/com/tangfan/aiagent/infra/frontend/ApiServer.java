package com.tangfan.aiagent.infra.frontend;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

public class ApiServer {
    private static final int DEFAULT_PORT = 7070;
    private final Gson gson = new Gson();

    public static void main(String[] args) {
        new ApiServer().start(DEFAULT_PORT);
    }

    public void start(int port) {
        Javalin.create(config -> {
            config.routes.get("/health", this::health);
            config.routes.post("/api/echo", this::echo);
        }).start(port);
    }

    private void health(Context ctx) {
        json(ctx, 200, Map.of("status", "ok"));
    }

    private void echo(Context ctx) {
        final JsonElement body;
        try {
            body = JsonParser.parseString(ctx.body());
        } catch (JsonParseException exception) {
            json(ctx, 400, Map.of("error", "Request body must be valid JSON"));
            return;
        }

        if (!body.isJsonObject()) {
            json(ctx, 400, Map.of("error", "Request body must be a JSON object"));
            return;
        }

        JsonObject request = body.getAsJsonObject();
        JsonElement message = request.get("message");
        if (message == null || !message.isJsonPrimitive() || !message.getAsJsonPrimitive().isString()
                || message.getAsString().isBlank()) {
            json(ctx, 400, Map.of("error", "'message' must be a non-blank string"));
            return;
        }

        json(ctx, 200, Map.of("message", message.getAsString()));
    }

    private void json(Context ctx, int status, Object body) {
        ctx.status(status)
                .contentType("application/json")
                .result(gson.toJson(body));
    }
}
