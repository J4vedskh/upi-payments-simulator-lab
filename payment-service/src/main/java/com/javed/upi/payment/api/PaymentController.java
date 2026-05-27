package com.javed.upi.payment.api;

import com.javed.upi.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping
  public ResponseEntity<PaymentResponse> submit(@Valid @RequestBody PaymentRequest request) {
    return ResponseEntity.accepted().body(paymentService.submit(request));
  }

  @GetMapping("/{paymentId}")
  public PaymentResponse find(@PathVariable String paymentId) {
    return paymentService.find(paymentId);
  }
}

