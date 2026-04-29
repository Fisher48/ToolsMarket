package ru.fisher.ToolsMarket.dto;

import lombok.Builder;

@Builder
public record UserRegistrationEvent(
        Long userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone
) { }
