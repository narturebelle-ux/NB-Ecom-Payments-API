package com.narturebelle.payments.care;

import com.narturebelle.payments.model.Role;
import com.narturebelle.payments.security.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Private calls to the Care API, which owns users, roles and consent. Used once care.base-url is set. */
@Component
@ConditionalOnExpression("!'${care.base-url:}'.isEmpty()")
public class HttpCareDirectory implements CareDirectory {

    private record Identity(Long userId, Set<String> roles) {}

    private record ConsentStatus(List<String> pending) {}

    private record CachedCaller(CallerProfile caller, Instant expiresAt) {}

    private static final Duration CALLER_CACHE = Duration.ofSeconds(60);
    private static final int CALLER_CACHE_SIZE = 1000;

    private final RestClient restClient;
    private final Map<String, CachedCaller> callers = new ConcurrentHashMap<>();

    @Autowired
    public HttpCareDirectory(@Value("${care.base-url}") String baseUrl, @Value("${internal.api-token:}") String token) {
        this(client(RestClient.builder(), baseUrl, token));
    }

    HttpCareDirectory(RestClient restClient) {
        this.restClient = restClient;
    }

    static RestClient client(RestClient.Builder builder, String baseUrl, String token) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(3000);
        timeouts.setReadTimeout(10000);
        return builder.requestFactory(timeouts)
                .baseUrl(baseUrl)
                .defaultHeader(InternalTokenFilter.HEADER, token)
                .build();
    }

    /** Only real users are cached, so a first sign-in is seen at once. */
    @Override
    public Optional<CallerProfile> findCaller(String firebaseUid) {
        CachedCaller cached = callers.get(firebaseUid);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return Optional.of(cached.caller());
        }
        Identity identity;
        try {
            identity = restClient.get().uri("/internal/v1/users/by-firebase-uid/{uid}", firebaseUid).retrieve().body(Identity.class);
        } catch (HttpClientErrorException.NotFound noProfileYet) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new CareUnavailableException("Could not look up the caller in the Care API", e);
        }
        CallerProfile caller = new CallerProfile(identity.userId(), knownRoles(identity.roles()));
        if (callers.size() >= CALLER_CACHE_SIZE) {
            callers.clear();
        }
        callers.put(firebaseUid, new CachedCaller(caller, Instant.now().plus(CALLER_CACHE)));
        return Optional.of(caller);
    }

    @Override
    public List<String> pendingConsents(Long userId) {
        try {
            return restClient.get().uri("/internal/v1/users/{id}/consent-status", userId).retrieve().body(ConsentStatus.class).pending();
        } catch (RestClientException e) {
            throw new CareUnavailableException("Could not read consent status from the Care API", e);
        }
    }

    /** A role Care added that this API doesn't know grants nothing here, rather than failing the caller's sign-in. */
    private static Set<Role> knownRoles(Set<String> names) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (Role role : Role.values()) {
            if (names.contains(role.name())) {
                roles.add(role);
            }
        }
        return roles;
    }
}
