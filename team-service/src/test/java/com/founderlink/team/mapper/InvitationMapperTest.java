package com.founderlink.team.mapper;

import com.founderlink.team.dto.request.InvitationRequestDto;
import com.founderlink.team.dto.response.InvitationResponseDto;
import com.founderlink.team.entity.Invitation;
import com.founderlink.team.entity.InvitationStatus;
import com.founderlink.team.entity.TeamRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationMapperTest {

    private InvitationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new InvitationMapper();
    }

    @Test
    void toEntity_ShouldMapCorrectly() {
        // Arrange
        InvitationRequestDto dto = new InvitationRequestDto();
        dto.setStartupId(1L);
        dto.setInvitedUserId(2L);
        dto.setRole(TeamRole.CTO);
        Long founderId = 5L;

        // Act
        Invitation entity = mapper.toEntity(dto, founderId);

        // Assert
        assertThat(entity).isNotNull();
        assertThat(entity.getStartupId()).isEqualTo(1L);
        assertThat(entity.getInvitedUserId()).isEqualTo(2L);
        assertThat(entity.getRole()).isEqualTo(TeamRole.CTO);
        assertThat(entity.getFounderId()).isEqualTo(5L);
    }

    @Test
    void toResponseDto_ShouldMapCorrectly() {
        // Arrange
        Invitation entity = new Invitation();
        entity.setId(10L);
        entity.setStartupId(1L);
        entity.setFounderId(5L);
        entity.setInvitedUserId(2L);
        entity.setRole(TeamRole.ENGINEERING_LEAD);
        entity.setStatus(InvitationStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        // Act
        InvitationResponseDto response = mapper.toResponseDto(entity);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getStartupId()).isEqualTo(1L);
        assertThat(response.getFounderId()).isEqualTo(5L);
        assertThat(response.getInvitedUserId()).isEqualTo(2L);
        assertThat(response.getRole()).isEqualTo(TeamRole.ENGINEERING_LEAD);
        assertThat(response.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }
}
