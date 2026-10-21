package com.javed.upi.payment.config;

import com.javed.upi.fraud.FraudEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FraudThresholdProperties.class)
public class FraudConfiguration {
  @Bean
  FraudEngine fraudEngine(FraudThresholdProperties properties) {
    return FraudEngine.withAmountThresholds(
        properties.reviewThreshold(),
        properties.blockThreshold());
  }
}

