package com.microservices.Monolith_example.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController
{
    @RequestMapping("/200")
    // /200 and /200/ are different
    //Using Request instead of Get is fine
    public String test_msg()
    {
        return "yo brotha! hello!";
    }
}
