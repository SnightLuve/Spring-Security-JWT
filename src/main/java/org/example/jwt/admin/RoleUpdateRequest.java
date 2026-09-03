package org.example.jwt.admin;

import jakarta.validation.constraints.NotEmpty;
import org.example.jwt.model.Role;

import java.util.Set;

public record RoleUpdateRequest(@NotEmpty Set<Role> roles) {
}
