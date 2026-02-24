package com.microservices.Monolith_example.Configuration.Security;
import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class UserConfig {
    @Bean
    public UserDetailsService userDetailsService()
    {
        UserDetails buyer = User.withUsername("Aryan")
                .password(passwordEncoder().encode("pass123"))
                .roles("buyer")
                .build();
        UserDetails seller = User.withUsername("Aditya")
                .password(passwordEncoder().encode("pass123"))
                .roles("seller")
                .build();
        UserDetails admin = User.withUsername("DBA")
                .password(passwordEncoder().encode("pass123"))
                .roles("admin")
                .build();
        return new InMemoryUserDetailsManager(buyer, seller, admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }
}