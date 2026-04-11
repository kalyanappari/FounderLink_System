package com.founderlink.startup.mapper;

import com.founderlink.startup.dto.request.StartupRequestDto;
import com.founderlink.startup.dto.response.StartupResponseDto;
import com.founderlink.startup.entity.Startup;
import com.founderlink.startup.entity.StartupStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StartupMapperTest {

    private StartupMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new StartupMapper();
    }

    @Test
    void toEntity_shouldMapCorrectly() {
        StartupRequestDto requestDto = new StartupRequestDto();
        requestDto.setName("Test Startup");
        requestDto.setDescription("Description");
        requestDto.setIndustry("Tech");
        requestDto.setProblemStatement("Problem");
        requestDto.setSolution("Solution");
        requestDto.setFundingGoal(new BigDecimal("1000000.00"));
        requestDto.setStage(StartupStage.IDEA);

        Startup entity = mapper.toEntity(requestDto, 123L);

        assertThat(entity.getName()).isEqualTo("Test Startup");
        assertThat(entity.getDescription()).isEqualTo("Description");
        assertThat(entity.getIndustry()).isEqualTo("Tech");
        assertThat(entity.getProblemStatement()).isEqualTo("Problem");
        assertThat(entity.getSolution()).isEqualTo("Solution");
        assertThat(entity.getFundingGoal()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(entity.getStage()).isEqualTo(StartupStage.IDEA);
        assertThat(entity.getFounderId()).isEqualTo(123L);
    }

    @Test
    void toResponseDto_shouldMapCorrectly() {
        Startup startup = new Startup();
        startup.setId(1L);
        startup.setName("Test Startup");
        startup.setDescription("Description");
        startup.setIndustry("Tech");
        startup.setProblemStatement("Problem");
        startup.setSolution("Solution");
        startup.setFundingGoal(new BigDecimal("1000000.00"));
        startup.setStage(StartupStage.IDEA);
        startup.setFounderId(456L);
        startup.setCreatedAt(LocalDateTime.now());

        StartupResponseDto responseDto = mapper.toResponseDto(startup);

        assertThat(responseDto.getId()).isEqualTo(1L);
        assertThat(responseDto.getName()).isEqualTo("Test Startup");
        assertThat(responseDto.getDescription()).isEqualTo("Description");
        assertThat(responseDto.getIndustry()).isEqualTo("Tech");
        assertThat(responseDto.getProblemStatement()).isEqualTo("Problem");
        assertThat(responseDto.getSolution()).isEqualTo("Solution");
        assertThat(responseDto.getFundingGoal()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(responseDto.getStage()).isEqualTo(StartupStage.IDEA);
        assertThat(responseDto.getFounderId()).isEqualTo(456L);
        assertThat(responseDto.getCreatedAt()).isNotNull();
    }
}
