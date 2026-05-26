package com.workshop.port.out;

import com.workshop.domain.model.AppUser;

import java.util.Optional;

public interface UserRepository {

    Optional<AppUser> findByUsername(String username);
}
