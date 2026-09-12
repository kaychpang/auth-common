package com.example.authcommon.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  boolean existsByAuth0UserIdAndActiveTrue(String auth0UserId);
}
