package com.wind.service;

import io.javalin.http.Context;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class ProxyService {
    private final HttpClient httpClient;
    
    // Cabeçalhos que são específicos da conexão e não devem ser repassados
    private static final List<String> HOP_BY_HOP_HEADERS = List.of(
        "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
        "TE", "Trailers", "Transfer-Encoding", "Upgrade", "Host"
    );

    public ProxyService() {
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }

    public void proxy(Context ctx, String targetAddress) {
        try {
            String path = ctx.path();
            String queryString = ctx.queryString() == null ? "" : "?" + ctx.queryString();
            
            URI targetUri = new URI("http://" + targetAddress + path + queryString);

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(ctx.bodyAsBytes());
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(targetUri)
                .method(ctx.method().toString(), bodyPublisher);
            
            // Copia os cabeçalhos da requisição original para a nova requisição
            ctx.headerMap().forEach((header, value) -> {
                if (!HOP_BY_HOP_HEADERS.contains(header)) {
                    requestBuilder.header(header, value);
                }
            });

            HttpResponse<byte[]> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
            
            // Repassa a resposta do serviço de backend para o cliente original
            ctx.status(response.statusCode());
            response.headers().map().forEach((header, values) -> {
                if (!HOP_BY_HOP_HEADERS.contains(header)) {
                    // Alguns cabeçalhos podem ter múltiplos valores
                    for (String value : values) {
                         ctx.header(header, value);
                    }
                }
            });

            if (response.body() != null) {
                ctx.result(response.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(502).result("Bad Gateway: " + e.getMessage());
        }
    }
}