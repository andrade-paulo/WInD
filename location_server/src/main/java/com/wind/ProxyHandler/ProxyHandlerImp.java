package com.wind.ProxyHandler;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.wind.interfaces.ProxyHandlerInterface;
import com.wind.entities.ProxyEntity;


public class ProxyHandlerImp extends UnicastRemoteObject implements ProxyHandlerInterface {
    private List<ProxyEntity> proxies = new ArrayList<>();
    private AtomicInteger currentProxyIndex = new AtomicInteger(0);

    public ProxyHandlerImp() throws RemoteException {
        super();
        startHeartbeatChecker(); // Inicia o verificador de heartbeat
    }

    @Override
    public void registerProxy(String address, int port, int heartbeatPort, int rmiReplicaPort, String multicastAdress, int multicastPort) throws RemoteException {
        synchronized (proxies) {
            proxies.add(new ProxyEntity(address, port, heartbeatPort, rmiReplicaPort, multicastAdress, multicastPort));
            System.out.println("Proxy registered: " + address + ":" + port);
        }
    }

    @Override
    public String getNextProxyAddress() throws RemoteException {
        synchronized (proxies) {
            if (proxies.isEmpty()) {
                throw new RemoteException("No proxies available");
            }
            int index = currentProxyIndex.getAndIncrement() % proxies.size();
            return proxies.get(index).getAddress();

            // Random access to proxies
            // int index = (int) (Math.random() * proxies.size());
            // return proxies.get(index).getAddress();
        }
    }

    @Override
    public int getNextProxyPort() throws RemoteException {
        synchronized (proxies) {
            if (proxies.isEmpty()) {
                throw new RemoteException("No proxies available");
            }
            int index = (currentProxyIndex.get() - 1) % proxies.size();
            return proxies.get(index).getPort();
        }
    }


    @Override
    public String getMulticastAddress() throws RemoteException {
        synchronized (proxies) {
            if (proxies.isEmpty()) {
                throw new RemoteException("No proxies available");
            }
            int index = currentProxyIndex.getAndIncrement() % proxies.size();
            return proxies.get(index).getMulticastAddress(); // Retorna o multicast do primeiro proxy
        }
    }

    @Override
    public int getMulticastPort() throws RemoteException {
        synchronized (proxies) {
            if (proxies.isEmpty()) {
                throw new RemoteException("No proxies available");
            }
            int index = (currentProxyIndex.get() - 1) % proxies.size();
            return proxies.get(index).getMulticastPort(); // Retorna o multicast do primeiro proxy
        }
    }


    private void startHeartbeatChecker() {
        Thread heartbeatThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000); // Verifica a cada 10 segundos
                    checkProxiesAvailability();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        heartbeatThread.setDaemon(true); // Define como thread daemon para encerrar com o programa
        heartbeatThread.start();
    }

    private void checkProxiesAvailability() {
        synchronized (proxies) {
            List<ProxyEntity> proxiesToRemove = new ArrayList<>();
            for (ProxyEntity proxy : proxies) {
                if (!isProxyAlive(proxy)) {
                    System.out.println("Proxy " + proxy + " is down. Removing from the list.");
                    proxiesToRemove.add(proxy);
                }
            }
            proxies.removeAll(proxiesToRemove);
        }
    }

    private boolean isProxyAlive(ProxyEntity proxy) {
        try (Socket socket = new Socket(proxy.getAddress(), proxy.getHeartbeatPort())) {
            sendProxiesList(socket);
            return true;
        } catch (IOException e) {
            // Se a conexão falhar, o proxy está indisponível
            return false;
        }
    }

    private void sendProxiesList(Socket socket) throws IOException {
        synchronized (proxies) {
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                out.writeObject(proxies);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}