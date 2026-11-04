package com.gateway.gateway.filter;

import com.gateway.gateway.core.TokenBucket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Order(1)
public class RateLimiterFilter implements WebFilter {

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    private RedisScript<Long> rateLimiterScript;

    private final WebClient webClient = WebClient.create("http://localhost:8080");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain){
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        //Define the unique Redis key for this user
        String redisKey = "rate_limit:" + clientIp;

        //Define our rate limiter parameters
        String capacity = "1";
        String refillRatePerSecond = "1";
        String now = String.valueOf(System.currentTimeMillis());

        //Execute the Lua Script inside Redis reactively
        return redisTemplate.execute(
                        rateLimiterScript,                 //The pre-compiled Lua script configuration
                        List.of(redisKey),                 //Sent to KEYS[1] in Lua
                        List.of(capacity, String.valueOf(refillRatePerSecond), now) //Sent to ARGV[1], ARGV[2], ARGV[3]
                )
                .next() //Get the first emitted result (our 1 or 0 from Lua)
                .flatMap(allowedResult -> {
                    //Check if Lua returned 1 (Allowed)
                    if (allowedResult == 1L) {
                        return proxyRequestToBackend(exchange); // Move to the next filter/route
                    }

                    //if Lua returned 0 (Blocked), intercept and return 429
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return response.setComplete();
                });
    }
    private Mono<Void> proxyRequestToBackend(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // Dynamically match the HTTP Method (GET, POST, etc.) and the path string
        return webClient.method(request.getMethod())
                .uri(request.getPath().value())
                .headers(headers -> headers.addAll(request.getHeaders()))
                .body(request.getBody(), org.springframework.core.io.buffer.DataBuffer.class)
                .exchangeToMono(backendResponse -> {
                    // Set the gateway response status and headers to match the backend exactly
                    response.setStatusCode(backendResponse.statusCode());
                    response.getHeaders().addAll(backendResponse.headers().asHttpHeaders());
                    // Pipe the data body stream back out to the client socket
                    return response.writeWith(backendResponse.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class));
                });
    }
}

