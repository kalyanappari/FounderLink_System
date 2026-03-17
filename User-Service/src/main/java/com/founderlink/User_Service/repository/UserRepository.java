package com.founderlink.User_Service.repository;

import com.founderlink.User_Service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
