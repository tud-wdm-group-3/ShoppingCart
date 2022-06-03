package com.wdsm.orderwrapper;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OrderWrapperController {

    private final OrderWrapperService orderWrapperService;
    @Autowired
    private EurekaClient eurekaClient;

    @RequestMapping(value="/create/{userId}")
    public ResponseEntity<String> create(@PathVariable(name="userId") int userId){
        List<InstanceInfo> instanceInfo = eurekaClient.getApplication("ORDER").getInstances();
        String res=orderWrapperService.clientCreate(userId);
        return ResponseEntity.ok().body(res);
    }
}
