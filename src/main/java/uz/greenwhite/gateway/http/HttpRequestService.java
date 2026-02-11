package uz.greenwhite.gateway.http;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import uz.greenwhite.gateway.model.kafka.RequestMessage;
import uz.greenwhite.gateway.model.kafka.ResponseMessage;
import uz.greenwhite.gateway.oauth2.OAuth2ProviderService;
import uz.greenwhite.gateway.oauth2.model.Token;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpRequestService {

    private final WebClient webClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final OAuth2ProviderService oAuth2ProviderService;

    private CircuitBreaker circuitBreaker;

    @PostConstruct
    public void init() {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("externalApi");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("Circuit Breaker state change: {}", event.getStateTransition()))
                .onFailureRateExceeded(event ->
                        log.warn("Circuit Breaker failure rate exceeded: {}%", event.getFailureRate()))
                .onSlowCallRateExceeded(event ->
                        log.warn("Circuit Breaker slow call rate exceeded: {}%", event.getSlowCallRate()));
    }

    /**
     * Send HTTP request with Circuit Breaker protection and OAuth2 support.
     * Returns a Mono that resolves to a ResponseMessage (success or error).
     */
    public Mono<ResponseMessage> sendRequest(RequestMessage request) {
        String compositeId = request.getCompositeId();

        // 1. If Circuit Breaker is OPEN — reject immediately
        try {
            circuitBreaker.acquirePermission();
        } catch (CallNotPermittedException ex) {
            log.warn("Circuit breaker OPEN — request blocked: {}", compositeId);
            return Mono.just(buildCircuitBreakerResponse(request));
        }

        // 2. Add OAuth2 Authorization header (fails fast if token unavailable)
        Map<String, String> headers;
        try {
            headers = addAuthorizationHeader(request);
        } catch (OAuth2TokenException e) {
            log.error("OAuth2 token failed, skipping HTTP call: {}", compositeId);
            circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.NANOSECONDS);
            return Mono.just(buildOAuth2ErrorResponse(request, e.getMessage()));
        }

        long startTime = System.nanoTime();
        String fullUrl = buildFullUrl(request);
        HttpMethod method = HttpMethod.valueOf(request.getMethod().toUpperCase());

        log.info("Sending HTTP request: {} {} -> {}", method, fullUrl, compositeId);

        return webClient
                .method(method)
                .uri(fullUrl)
                .headers(h -> applyHeaders(h, headers))
                .bodyValue(request.getBody() != null ? request.getBody() : "")
                .retrieve()
                .toEntity(String.class)
                .map(entity -> {
                    long duration = System.nanoTime() - startTime;
                    int status = entity.getStatusCode().value();
                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);

                    log.info("HTTP response: {} -> status={}, time={}ms",
                            compositeId, status, duration / 1_000_000);

                    String contentType = entity.getHeaders().getContentType() != null
                            ? entity.getHeaders().getContentType().toString()
                            : MediaType.APPLICATION_JSON_VALUE;

                    return buildSuccessResponse(request, status, contentType, entity.getBody());
                })
                .onErrorResume(ex -> {
                    long duration = System.nanoTime() - startTime;
                    circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS, ex);
                    log.error("HTTP request failed: {} -> {}", compositeId, ex.getMessage());
                    return Mono.just(buildErrorResponse(request, ex));
                });
    }

    // ==================== OAUTH2 ====================

    /**
     * Get OAuth2 token and add Authorization header.
     * Throws OAuth2TokenException if token acquisition fails.
     */
    private Map<String, String> addAuthorizationHeader(RequestMessage request) {
        Map<String, String> headers = request.getHeaders() != null
                ? new HashMap<>(request.getHeaders())
                : new HashMap<>();

        if (request.getOauth2Provider() == null) {
            return headers;
        }

        try {
            Token token = oAuth2ProviderService.getToken(request.getOauth2Provider());
            headers.put(HttpHeaders.AUTHORIZATION, token.getAuthorizationHeader());
            log.debug("OAuth2 Authorization header added for provider: {}", request.getOauth2Provider());
        } catch (Exception e) {
            log.error("Failed to get OAuth2 token for provider {}: {}",
                    request.getOauth2Provider(), e.getMessage());
            throw new OAuth2TokenException(
                    "OAuth2 token acquisition failed for provider: " + request.getOauth2Provider(), e);
        }

        return headers;
    }

    // ==================== HELPERS ====================

    /**
     * Build full URL from base URL, URI path, and query parameters.
     */
    private String buildFullUrl(RequestMessage request) {
        StringBuilder url = new StringBuilder(request.getBaseUrl());

        if (request.getUri() != null && !request.getUri().isEmpty()) {
            url.append(request.getUri());
        }

        if (request.getParams() != null && !request.getParams().isEmpty()) {
            url.append(request.getParams());
        }

        return url.toString();
    }

    /**
     * Apply custom headers to the HTTP request.
     * Sets default Content-Type to JSON only if not already provided.
     */
    private void applyHeaders(HttpHeaders httpHeaders, Map<String, String> customHeaders) {
        if (customHeaders != null) {
            customHeaders.forEach(httpHeaders::add);
        }

        if (httpHeaders.getContentType() == null) {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
    }

    /**
     * Build response for when Circuit Breaker is OPEN (503).
     */
    private ResponseMessage buildCircuitBreakerResponse(RequestMessage request) {
        return ResponseMessage.builder()
                .companyId(request.getCompanyId())
                .requestId(request.getRequestId())
                .httpStatus(503)
                .errorMessage("Circuit breaker is OPEN: external API unavailable")
                .errorSource("CIRCUIT_BREAKER")
                .processedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Build response for OAuth2 token acquisition failure (401).
     */
    private ResponseMessage buildOAuth2ErrorResponse(RequestMessage request, String errorMessage) {
        return ResponseMessage.builder()
                .companyId(request.getCompanyId())
                .requestId(request.getRequestId())
                .httpStatus(401)
                .errorMessage(errorMessage)
                .errorSource("OAUTH2")
                .processedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Build response for successful HTTP call.
     */
    private ResponseMessage buildSuccessResponse(RequestMessage request, int status,
                                                 String contentType, String body) {
        return ResponseMessage.builder()
                .companyId(request.getCompanyId())
                .requestId(request.getRequestId())
                .httpStatus(status)
                .contentType(contentType)
                .body(body)
                .processedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Build response for failed HTTP call.
     * Extracts status code from WebClientResponseException if available.
     */
    private ResponseMessage buildErrorResponse(RequestMessage request, Throwable ex) {
        int httpStatus = 500;
        String errorMessage = ex.getMessage();

        if (ex instanceof WebClientResponseException wcEx) {
            httpStatus = wcEx.getStatusCode().value();
            errorMessage = wcEx.getResponseBodyAsString();
        }

        return ResponseMessage.builder()
                .companyId(request.getCompanyId())
                .requestId(request.getRequestId())
                .httpStatus(httpStatus)
                .errorMessage(errorMessage)
                .errorSource("HTTP")
                .processedAt(LocalDateTime.now())
                .build();
    }
}