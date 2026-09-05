package com.swiftpay.controller;

import com.swiftpay.dto.CreatePaymentRequest;
import com.swiftpay.dto.PaymentResponse;
import com.swiftpay.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Create payment")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentResponse response = paymentService.createPayment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(summary = "Get transaction history")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<PaymentResponse>> getTransactionHistory(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(
                paymentService.getTransactionHistory(userId)
        );
    }
}
