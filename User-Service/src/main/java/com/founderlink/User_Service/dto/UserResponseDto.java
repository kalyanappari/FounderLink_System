package com.founderlink.User_Service.dto;

import lombok.Data;

@Data
public class UserResponseDto {

    private Long id;
    private String name;
    private String email;
    private String skills;
    private String experience;
    private String bio;
    private String portfolioLinks;

}
