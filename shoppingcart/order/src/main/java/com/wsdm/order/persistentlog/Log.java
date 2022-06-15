package com.wsdm.order.persistentlog;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

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