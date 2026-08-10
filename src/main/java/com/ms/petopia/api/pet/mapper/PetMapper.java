package com.ms.petopia.api.pet.mapper;

import com.ms.petopia.api.pet.domain.Pet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PetMapper {

    int insertPet(Pet pet);

    List<Pet> selectPetsByUserId(@Param("userId") Long userId);

    Pet selectPetById(@Param("petId") Long petId);

    //부분 수정
    int updatePet(
            @Param("petId") Long petId,
            @Param("name") String name,
            @Param("species") String species,
            @Param("breed") String breed,
            @Param("birthDate") LocalDate birthDate,
            @Param("gender") String gender,
            @Param("isNeutered") Boolean isNeutered,
            @Param("imageUrl") String imageUrl
    );

    int deletePetById(@Param("petId") Long petId);
}
