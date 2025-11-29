package com.wind.entities;

import java.io.Serializable;

public class ClientEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int id;
    private String username;
    private String password;

    public ClientEntity() {}

    public ClientEntity(int id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }

    public ClientEntity(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "ClientEntity [id=" + id + ", username=" + username + "]";
    }
}
