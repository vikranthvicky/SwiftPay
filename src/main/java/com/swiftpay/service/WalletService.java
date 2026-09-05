package com.swiftpay.service;

import com.swiftpay.entity.User;
import com.swiftpay.entity.Wallet;
import com.swiftpay.repository.UserRepository;
import com.swiftpay.repository.WalletRepository;

import java.math.BigDecimal;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class WalletService {

    private static final String BALANCE_PREFIX = "swiftpay:balance:";
    private static final Duration BALANCE_TTL = Duration.ofMinutes(5);

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public WalletService(
            WalletRepository walletRepository,
            UserRepository userRepository,
            RedisTemplate<String, String> redisTemplate
    ) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Wallet createWallet(UUID userId) {
        return createWallet(userId, BigDecimal.ZERO);
    }

    @Transactional
    public Wallet createWallet(UUID userId, BigDecimal initialBalance) {
        if (initialBalance == null || initialBalance.signum() < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        if (walletRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("Wallet already exists for this user");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setBalance(initialBalance);
        Wallet saved = walletRepository.save(wallet);
        cacheBalance(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Wallet getWalletByUserId(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        String cachedBalance = redisTemplate.opsForValue().get(BALANCE_PREFIX + userId);
        if (cachedBalance != null) {
            wallet.setBalance(new BigDecimal(cachedBalance));
        } else {
            cacheBalance(wallet);
        }
        return wallet;
    }

    public void cacheBalance(Wallet wallet) {
        redisTemplate.opsForValue().set(
                BALANCE_PREFIX + wallet.getUser().getId(),
                wallet.getBalance().toPlainString(),
                BALANCE_TTL
        );
    }
}
