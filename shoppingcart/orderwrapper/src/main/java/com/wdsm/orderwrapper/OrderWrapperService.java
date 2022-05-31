package com.wdsm.orderwrapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderWrapperService {

    private final OrderClient orderClient;

    public String clientCreate(int userId){
        return orderClient.clientCreate(userId);
    }
}
