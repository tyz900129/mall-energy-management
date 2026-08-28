package com.wanyuecity.energy.security;

import com.wanyuecity.energy.system.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter from {@link SysUser} to Spring Security's {@link UserDetails}. Roles are
 * exposed as {@code ROLE_<CODE>} authorities.
 */
@Getter
public class LoginUserDetails implements UserDetails {

    private final SysUser user;
    private final List<String> roleCodes;

    public LoginUserDetails(SysUser user, List<String> roleCodes) {
        this.user = user;
        this.roleCodes = roleCodes == null ? List.of() : roleCodes;
    }

    public Long getId() {
        return user == null ? null : user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roleCodes.stream()
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return user == null ? null : user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user == null ? null : user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user != null && Integer.valueOf(1).equals(user.getStatus());
    }
}

