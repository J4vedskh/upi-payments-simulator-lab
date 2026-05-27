package com.javed.upi.payment.config;

import com.javed.upi.fraud.FraudEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FraudConfiguration {
  @Bean
  FraudEngine fraudEngine() {
    return FraudEngine.defaultEngine();
  }
}

