package com.wdsm.orderwrapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Component
public class OrderPartitionClient {
    FeignClientBuilder feignClientBuilder;
    public OrderPartitionClient(@Autowired ApplicationContext applicationContext){
        this.feignClientBuilder=new FeignClientBuilder(applicationContext);
    }

    interface CustomCall {
        @PostMapping("orders/create/{userId}")
        public String clientCreate(@PathVariable(name = "userId") int userId);
    }
    public String delegateClientCreate(int userid,int partitionId){
        return delegate(partitionId).clientCreate(userid);
    }

    public CustomCall delegate(int partitionId){
        String serviceName="order";
        if(partitionId>0){
            serviceName+="-"+partitionId;
        }
        return this.feignClientBuilder.forType(CustomCall.class,serviceName).build();
    }

}
