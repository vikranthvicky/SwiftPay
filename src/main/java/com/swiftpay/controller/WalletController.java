package com.swiftpay.controller;

import com.swiftpay.entity.Wallet;
import com.swiftpay.service.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<WalletResponse> createWallet(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0.00") BigDecimal initialBalance
    ) {
        Wallet wallet = walletService.createWallet(userId, initialBalance);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(wallet));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<WalletResponse> getWallet(
            @PathVariable UUID userId
    ) {
        Wallet wallet = walletService.getWalletByUserId(userId);

        return ResponseEntity.ok(toResponse(wallet));
    }

    private WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }

    public record WalletResponse(
            UUID id,
            UUID userId,
            BigDecimal balance,
            String currency,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}