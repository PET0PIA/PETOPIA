package com.ms.petopia.api.pet.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PetResponse(
        Long petId,
        String name,
        String species,
        String breed,
        LocalDate birthDate,
        String gender,
        Boolean isNeutered,
        String imageUrl,
        LocalDateTime createdAt
) {
}
