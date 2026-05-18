package com.yuzhi.dts.opmanager.web.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping(value = { "/", "/{path:^(?!api$|actuator$)[^\\.]*$}" })
    public String forward() {
        return "forward:/index.html";
    }
}
