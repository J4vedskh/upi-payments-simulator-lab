package com.javed.upi.payment.config;

import com.javed.upi.payment.service.PaymentIdempotencyStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentIdempotencyProperties.class)
public class PaymentIdempotencyConfiguration {

  @Bean
  PaymentIdempotencyStore paymentIdempotencyStore(PaymentIdempotencyProperties properties) {
    return new PaymentIdempotencyStore(properties);
  }
}
