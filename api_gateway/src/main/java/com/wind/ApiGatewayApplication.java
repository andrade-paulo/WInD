package com.wind;

import com.wind.config.ApiKeyStore;
import com.wind.service.DiscoveryService;
import com.wind.service.ProxyService;
import io.javalin.Javalin;
import io.javalin.http.Handler; // Importe o Handler
import java.util.Map;
import java.util.Optional;

public class ApiGatewayApplication {

    private static final Map<String, String> SERVICE_ROUTE_MAP = Map.of(
        "app", "application-server",
        "broker", "wing-egress-broker",
        "services", "service-discovery"
    );

    public static void main(String[] args) {
        ApiKeyStore apiKeyStore = new ApiKeyStore();
        DiscoveryService discoveryService = new DiscoveryService();
        ProxyService proxyService = new ProxyService();

        Javalin app = Javalin.create().start("0.0.0.0", 8000);
        System.out.println("API Gateway iniciado na porta 8000.");

        // Criamos uma única instância do nosso Handler para reutilizá-lo
        Handler gatewayHandler = ctx -> {
            // 1. Autenticação
            String apiKey = ctx.header("X-API-Key");
            if (!apiKeyStore.isValid(apiKey)) {
                System.out.println("Chave de API inválida: " + apiKey);
                ctx.status(401).result("Unauthorized");
                return;
            }
            System.out.println("Cliente autenticado com sucesso.");

            // 2. Mapeamento da rota para o serviço
            String[] pathSegments = ctx.path().split("/");
            if (pathSegments.length < 2) {
                ctx.status(404).result("Not Found: Rota de serviço inválida.");
                return;
            }
            
            String servicePrefix = pathSegments[1];
            String serviceName = SERVICE_ROUTE_MAP.get(servicePrefix);

            if (serviceName == null) {
                ctx.status(404).result("Not Found: Serviço para a rota '" + servicePrefix + "' não encontrado.");
                return;
            }
            System.out.println("Requisição mapeada para o serviço: " + serviceName);

            // 3. Descoberta de Serviço
            Optional<String> targetAddress = discoveryService.discoverServiceAddress(serviceName);

            if (targetAddress.isEmpty()) {
                ctx.status(503).result("Service Unavailable: " + serviceName);
                return;
            }
            
            String address = targetAddress.get();
            System.out.println("Encaminhando para: " + address);

            // 4. Proxy
            proxyService.proxy(ctx, address);
        };

        // Endpoint para descoberta direta de serviços (usado por clientes como eng_client)
        app.get("/discovery/{serviceName}", ctx -> {
            String apiKey = ctx.header("X-API-Key");
            if (!apiKeyStore.isValid(apiKey)) {
                ctx.status(401).result("Unauthorized");
                return;
            }
            String serviceName = ctx.pathParam("serviceName");
            Optional<String> address = discoveryService.discoverServiceAddress(serviceName);
            
            if (address.isPresent()) {
                ctx.result(address.get());
            } else {
                ctx.status(404).result("Service not found");
            }
        });

        // CORREÇÃO: Registramos o mesmo handler para cada método HTTP em um caminho wildcard.
        // Esta é a forma mais compatível de criar um "catch-all".
        app.get("/*", gatewayHandler);
        app.post("/*", gatewayHandler);
        app.put("/*", gatewayHandler);
        app.delete("/*", gatewayHandler);
        app.patch("/*", gatewayHandler);
        app.head("/*", gatewayHandler);
        app.options("/*", gatewayHandler);
    }
}