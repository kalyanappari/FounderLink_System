package com.founderlink.team.mapper;

import com.founderlink.team.dto.response.TeamMemberResponseDto;
import com.founderlink.team.entity.TeamMember;
import com.founderlink.team.entity.TeamRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TeamMemberMapperTest {

    private TeamMemberMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TeamMemberMapper();
    }

    @Test
    void toResponseDto_ShouldMapCorrectly() {
        // Arrange
        TeamMember entity = new TeamMember();
        entity.setId(101L);
        entity.setStartupId(1L);
        entity.setUserId(5L);
        entity.setRole(TeamRole.MARKETING_HEAD);
        entity.setIsActive(true);
        entity.setJoinedAt(LocalDateTime.now().minusDays(10));
        entity.setLeftAt(null);

        // Act
        TeamMemberResponseDto response = mapper.toResponseDto(entity);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getStartupId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(5L);
        assertThat(response.getRole()).isEqualTo(TeamRole.MARKETING_HEAD);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getJoinedAt()).isNotNull();
        assertThat(response.getLeftAt()).isNull();
    }
}
