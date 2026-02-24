package com.microservices.Monolith_example.Configuration.Security;

import io.jsonwebtoken.security.Keys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;

@Configuration
public class JWTConfig
{
    @Bean
    public SecretKey secretKey()
    {
        return Keys.hmacShaKeyFor("secretkey123456789 secretkey123456789".getBytes());
        //key must have sufficient byte length -> default = 256
        //vl - Inject secretkey from application.properties later
        //above function expects bytes
        //string has a function for getBytes wohoo!
    }
}
