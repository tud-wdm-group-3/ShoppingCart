package com.wdsm.orderwrapper;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("orders")
public class OrderWrapperController {

    private final OrderWrapperService orderWrapperService;

    @RequestMapping(value="/create/{userId}")
    public ResponseEntity<String> create(@PathVariable(name="userId") int userId){
        String res=orderWrapperService.clientCreate(userId);
        return ResponseEntity.ok().body(res);
    }
}
