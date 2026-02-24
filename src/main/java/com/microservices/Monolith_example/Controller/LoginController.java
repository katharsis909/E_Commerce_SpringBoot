package com.microservices.Monolith_example.Controller;

import com.microservices.Monolith_example.Configuration.Security.JWTUtil;
import com.microservices.Monolith_example.RequestModels.RequestAuth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
//You had forgotten restcontroller
//lesson - unit test each code by chatgpt or by output verifying!
//do,because you are dumb!
@RequestMapping("/sign/")
//method name cannot be /login
//cannot be even signin
public class LoginController
{
    private final AuthenticationManager authManager;
    private final JWTUtil jwtUtil;

    @Autowired
    //little need of Autowired tho
    public LoginController(AuthenticationManager authManager, JWTUtil jwtUtil) {
        this.authManager = authManager;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/test")
    public String hello2()
    {
        return "test_success!";
    }

    @PostMapping("/")
    public ResponseEntity<?> login(@RequestBody RequestAuth requestAuth)
    //could have accessed requestAuth through @RequestHeader too
    //but for some reason that way is not standard
    {
        try {
            Authentication auth = authManager.authenticate( new UsernamePasswordAuthenticationToken( requestAuth.getUsername(),requestAuth.getPassword() ));
            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            String token = jwtUtil.generateToken(userDetails);
            return ResponseEntity.ok(token);

        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

    }
}
