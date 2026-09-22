package com.example.selfheal.dto;

public record AgentResponse(
        boolean success,
        int attempts,
        String code,
        String tests,
        String plan,
        String finalOutput) {
}