package com.founderlink.User_Service.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
public class UserRequestDto {

    private String name;
    private String email;
    private String skills;
    private String experience;
    private String bio;
    private String portfolioLinks;
}
