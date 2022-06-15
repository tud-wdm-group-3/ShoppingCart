package com.wsdm.payment.persistentlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Note that I implement Map instead of extend HashMap
 * in order to have control over which methods are used.
 * @param <V> type of object
 */
public class PersistentMap<V> implements Map<Integer, V> {

    private String mapName;
    private Map<Integer, V> innerMap;
    private Map<Integer, Integer> keyToId;

    private LogRepository logRepo;

    @Autowired
    private static ObjectMapper objectMapper;

    public PersistentMap(String mapName, LogRepository logRepo, Class<V> classAttr) {
        this.mapName = mapName;
        this.logRepo = logRepo;

        this.innerMap = new HashMap<>();
        this.keyToId = new HashMap<>();

        List<Log> logs = logRepo.findByMapName(mapName);
        for (Log log : logs) {
            try {
                innerMap.put(log.getKey(), objectMapper.readValue(log.getValue(), classAttr));
                keyToId.put(log.getKey(), log.getId());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public int size() {
        return innerMap.size();
    }

    @Override
    public boolean isEmpty() {
        return innerMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return innerMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return innerMap.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return innerMap.get(key);
    }

    @Override
    public V put(Integer key, V value) {
        logRepo.deleteById(keyToId.get(key));
        save(key, value);
        return innerMap.put(key, value);
    }

    @Override
    public V remove(Object key) {
        logRepo.deleteById(keyToId.remove(key));
        return innerMap.remove(key);
    }

    @Override
    public void putAll(Map<? extends Integer, ? extends V> m) {
        throw new AssertionError("Dont use this method please.");
    }

    @Override
    public void clear() {
        throw new AssertionError("Dont use this method please.");
    }

    @Override
    public Set<Integer> keySet() {
        return innerMap.keySet();
    }

    @Override
    public Collection<V> values() {
        return innerMap.values();
    }

    @Override
    public Set<Entry<Integer, V>> entrySet() {
        return innerMap.entrySet();
    }

    private void save(Integer key, V value) {
        try {
            Log log = new Log(mapName, key, objectMapper.writeValueAsString(value));
            logRepo.save(log);
            keyToId.put(key, log.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
