package com.wsdm.payment.persistentlog;


import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "Logs")
@Data
@NoArgsConstructor
public class Log {

    @Id
    @GeneratedValue
    private int id;

    private String mapName;
    private int key;
    private String value;

    public Log(String mapName, int key, String value)
    {
        this.mapName = mapName;
        this.key = key;
        this.value = value;
    }
}