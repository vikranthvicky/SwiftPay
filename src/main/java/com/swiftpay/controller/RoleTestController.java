package com.swiftpay.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RoleTestController {

    @GetMapping("/customer/test")
    public String customerTest() {
        return "Customer access granted";
    }

    @GetMapping("/merchant/test")
    public String merchantTest() {
        return "Merchant access granted";
    }

    @GetMapping("/admin/test")
    public String adminTest() {
        return "Admin access granted";
    }
}