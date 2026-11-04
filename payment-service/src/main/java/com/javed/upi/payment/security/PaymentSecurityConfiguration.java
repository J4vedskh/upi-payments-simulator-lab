package com.javed.upi.payment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javed.upi.payment.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration(proxyBeanMethods = false)
public class PaymentSecurityConfiguration {
  private static final int MINIMUM_HS256_KEY_BYTES = 32;
  private static final String PAYMENT_WRITE_AUTHORITY = "SCOPE_payment.write";

  @Bean
  @Profile("!secure")
  SecurityFilterChain defaultPaymentSecurity(HttpSecurity http) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .build();
  }

  @Bean
  @Profile("secure")
  SecurityFilterChain securePaymentSecurity(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
    AuthenticationEntryPoint authenticationEntryPoint = structuredAuthenticationEntryPoint(objectMapper);
    AccessDeniedHandler accessDeniedHandler = structuredAccessDeniedHandler(objectMapper);

    return http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(HttpMethod.POST, "/api/payments").hasAuthority(PAYMENT_WRITE_AUTHORITY)
            .requestMatchers(HttpMethod.GET, "/api/payments/**").permitAll()
            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
            .requestMatchers("/actuator/prometheus").permitAll()
            .anyRequest().permitAll())
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler))
        .oauth2ResourceServer(resourceServer -> resourceServer
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)
            .jwt(Customizer.withDefaults()))
        .build();
  }

  private static AuthenticationEntryPoint structuredAuthenticationEntryPoint(ObjectMapper objectMapper) {
    BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    return (request, response, exception) -> {
      delegate.commence(request, response, exception);
      writeSecurityError(
          response,
          objectMapper,
          HttpStatus.UNAUTHORIZED,
          "UNAUTHORIZED",
          "Bearer authentication is required.",
          request.getRequestURI());
    };
  }

  private static AccessDeniedHandler structuredAccessDeniedHandler(ObjectMapper objectMapper) {
    BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();
    return (request, response, exception) -> {
      delegate.handle(request, response, exception);
      writeSecurityError(
          response,
          objectMapper,
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Access is denied.",
          request.getRequestURI());
    };
  }

  private static void writeSecurityError(
      HttpServletResponse response,
      ObjectMapper objectMapper,
      HttpStatus status,
      String error,
      String message,
      String path) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(),
        ApiErrorResponse.of(status.value(), error, message, path, Map.of()));
  }

  @Bean
  @Profile("secure")
  JwtDecoder paymentJwtDecoder(@Value("${app.security.jwt.secret}") String encodedSecret) {
    byte[] secretBytes;
    try {
      secretBytes = Base64.getDecoder().decode(encodedSecret);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("PAYMENT_JWT_SECRET must be valid Base64.", exception);
    }

    if (secretBytes.length < MINIMUM_HS256_KEY_BYTES) {
      throw new IllegalStateException("PAYMENT_JWT_SECRET must decode to at least 32 bytes.");
    }

    SecretKeySpec secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }
}
