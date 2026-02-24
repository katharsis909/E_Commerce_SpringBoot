package com.microservices.Monolith_example.Configuration.Security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.stream.Collectors;

@Service
public class JWTUtil
{
    private final SecretKey secretKey;

    @Autowired
    public JWTUtil(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    public String generateToken(UserDetails userDetails)
    {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("roles", userDetails.getAuthorities()
                                .stream().map(GrantedAuthority::getAuthority)
                                .collect(Collectors.toList()))
                .setIssuedAt(new Date())
                .setExpiration( new Date(System.currentTimeMillis() + 1000*60*60) ) //valid for 1 hour
                .signWith(secretKey,SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims extractClaims(String token)
    {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    public String extractUsername(String token)
    {
        String username = extractClaims(token).getSubject();
        return username;
    }

    public boolean validateExpiration(String token)
    {
        Date d = extractClaims(token).getExpiration();
        long exp_time = d.getTime();
        long curr_time = System.currentTimeMillis();

        return exp_time > curr_time;
    }


}
