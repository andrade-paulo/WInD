package com.wind.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ProxyHandlerInterface extends Remote {
    void registerProxy(String address, int port, int heartbeatPort, int rmiReplicaPort, String muilticastAdress, int multicastPort) throws RemoteException;
    String getNextProxyAddress() throws RemoteException;
    int getNextProxyPort() throws RemoteException;

    String getMulticastAddress() throws RemoteException;
    int getMulticastPort() throws RemoteException;
}