package com.microservices.Monolith_example.Configuration.Security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;


@Configuration
@EnableWebSecurity
public class SecurityConfig
{
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public SecurityConfig(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.userDetailsService(userDetailsService)
                    .passwordEncoder(passwordEncoder);
        //password Encoder is needed to hash the received password and verify the hashed ones!
        return authBuilder.build();
    }



    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JWTRequestFilter jwtRequestFilter) throws Exception {
        http
                .csrf(csrf -> csrf
                        .disable())
                //ignore for understanding
                .authorizeHttpRequests(auth -> auth
                //.requestMatchers("/sign","/sign/","/sign/**").permitAll()
                .requestMatchers("/sign","/sign/**").permitAll()
                /*
                /sign and /sign/ are totally different
                /sign means sign is an end path and a resource, not a directory
                /sign/ can be used in the Controller's RequestMapping because then it will be a directory
                /sign/* and /sign/** are a bit different
                in requestMatchers - ** means every subpath inside, * means only one path inside
                /signin/"**"/verify, shows "**" -> is a kind of regular expression/
                 */
                .requestMatchers("/view/product/**").permitAll()
                                .requestMatchers("/search/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/order/**").hasRole("buyer")
                                .requestMatchers("/test/**").permitAll()
                .requestMatchers("/add/product/**").hasRole("seller")
                //h2-console/** allows both /h2-console & /h2-console/
                //only for testing
                // .anyRequest().permitAll()
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                //ignore
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                //no Session ID now
                .formLogin(form -> form.disable());
                //No default login page now

        //JWT is not default, so It meeds manual setting up in the filterChain
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
