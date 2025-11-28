package com.wind.datastructures;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.wind.model.DAO.LogDAO;

public class Hash<T> implements Serializable, Iterable<T> {
    @SuppressWarnings("hiding")
    private class Node<T> implements Serializable {
        int chave;
        T valor;

        public Node(int chave, T valor) {
            this.chave = chave;
            this.valor = valor;
        }
    }

    private Node<T>[] tabela;
    private int tamanho;
    private int ocupacao;
    private final Lock lock = new ReentrantLock(); // Lock for thread safety

    @SuppressWarnings("unchecked")
    public Hash(int tamanho) {
        if (tamanho < 1) {
            throw new IllegalArgumentException("Tamanho inválido");
        }
        this.tamanho = proximoPrimo(tamanho);
        tabela = new Node[this.tamanho];
        ocupacao = 0;
    }

    private int proximoPrimo(int n) {
        while (!isPrimo(n)) {
            n++;
        }

        return n;
    }

    private boolean isPrimo(int n) {
        // Só precisa verificar até a raiz quadrada de n
        for (int i = 2; i < Math.sqrt(n); i++) {
            if (n % i == 0) {
                return false;
            }
        }

        return true;
    }

    private int hash(int chave) {
        return chave % this.tamanho;
    }

    private int hashColisao(int chave, int i) {
        // Função de hash para tratamento de colisões por endereçamento aberto
        // A função somada garante a varredura de toda a tabela
        return (hash(chave) + (i + chave)) % this.tamanho;
    }

    private int hashColisao(int chave, int i, int novoTamanho) {
        return (hash(chave) + (i + chave)) % novoTamanho;
    }

    public void inserir(int chave, T valor) {
        lock.lock();
        try {
            int posicao = hash(chave);

            if (tabela[posicao] == null) {
                tabela[posicao] = new Node<>(chave, valor);
                ocupacao++;
            } else {
                // Se já existe a chave na posição inicial, atualiza o valor
                if (tabela[posicao].chave == chave) {
                    LogDAO.addLog("[HASH UPDATE] Chave " + chave + " atualizada");

                    tabela[posicao].valor = valor;
                    return;
                }

                int i = 1;
                while (tabela[hashColisao(chave, i)] != null) {
                    // Se já existe a chave, atualiza o valor
                    if (tabela[hashColisao(chave, i)].chave == chave) {
                        LogDAO.addLog("[HASH UPDATE] Atualizando valor da chave " + chave);

                        tabela[hashColisao(chave, i)].valor = valor;
                        return;
                    }
                    i++;
                }

                LogDAO.addLog("[HASH COLLISION] Colisão na posição " + posicao + " com chave " + chave);

                tabela[hashColisao(chave, i)] = new Node<>(chave, valor);
                ocupacao++;
            }

            if (ocupacao >= tamanho) {
                redimensionar(tamanho * 2);
            }
        } finally {
            lock.unlock();
        }
    }

    public T buscar(int chave) {
        lock.lock();
        try {
            int posicao = hash(chave);

            if (tabela[posicao] != null && tabela[posicao].chave == chave) {
                return tabela[posicao].valor;
            } else {
                int i = 1;
                while (tabela[hashColisao(chave, i)] != null && tabela[hashColisao(chave, i)].chave != chave) {
                    i++;
                }

                if (tabela[hashColisao(chave, i)] != null) {
                    return tabela[hashColisao(chave, i)].valor;
                }
            }

            return null;
        } finally {
            lock.unlock();
        }
    }

    public T remover(int chave) throws Exception {
        lock.lock();
        try {
            int posicao = hash(chave);

            if (tabela[posicao] != null && tabela[posicao].chave == chave) {
                // copy to return
                T value = tabela[posicao].valor;
                tabela[posicao] = null;
                ocupacao--;
                return value;
            } else {
                int i = 1;
                while (tabela[hashColisao(chave, i)] != null && tabela[hashColisao(chave, i)].chave != chave) {
                    i++;
                }

                if (tabela[hashColisao(chave, i)] != null) {
                    T value = tabela[hashColisao(chave, i)].valor;
                    tabela[hashColisao(chave, i)] = null;
                    ocupacao--;
                    return value;
                }
            }

            // Quem sobrar é exception
            throw new Exception("Chave não encontrada");
        } finally {
            lock.unlock();
        }
    }

    public void redimensionar(int novoTamanho) {
        lock.lock();
        try {
            novoTamanho = proximoPrimo(novoTamanho);

            LogDAO.addLog("[HASH RESIZE] Redimensionando tabela de " + tamanho + " para " + novoTamanho);

            @SuppressWarnings("unchecked")
            Node<T>[] novaTabela = new Node[novoTamanho];

            for (int i = 0; i < tamanho; i++) {
                if (tabela[i] != null) {
                    int chave = tabela[i].chave;
                    int posicao = chave % novoTamanho;

                    if (novaTabela[posicao] == null) {
                        novaTabela[posicao] = tabela[i];
                    } else {
                        int j = 1;
                        while (novaTabela[hashColisao(chave, j)] != null) {
                            j++;
                        }

                        novaTabela[hashColisao(chave, j, novoTamanho)] = tabela[i];
                    }
                }
            }

            tabela = novaTabela;
            tamanho = novoTamanho;
        } finally {
            lock.unlock();
        }
    }

    public int getOcupacao() {
        lock.lock();
        try {
            return ocupacao;
        } finally {
            lock.unlock();
        }
    }

    public int getTamanho() {
        lock.lock();
        try {
            return tamanho;
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return ocupacao == 0;
        } finally {
            lock.unlock();
        }
    }

    public T getUltimo() {
        lock.lock();
        try {
            Node<T> maior = tabela[0];

            for (int i = 1; i < tamanho; i++) {
                if (maior == null) {
                    maior = tabela[i];
                } else if (tabela[i] != null && tabela[i].chave > maior.chave) {
                    maior = tabela[i];
                }
            }

            if (maior == null) {
                return null;
            }

            return maior.valor;
        } finally {
            lock.unlock();
        }
    }

    public List<T> getAll() {
        lock.lock();
        try {
            List<T> list = new ArrayList<>();
            for (int i = 0; i < tamanho; i++) {
                if (tabela[i] != null) {
                    list.add(tabela[i].valor);
                }
            }
            return list;
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        lock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[");

            for (int i = 0; i < tamanho; i++) {
                if (tabela[i] != null) {
                    sb.append(tabela[i].chave);
                } else {
                    sb.append("null");
                }

                if (i < tamanho - 1) {
                    sb.append(", ");
                }
            }

            sb.append("]");
            return sb.toString();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < ocupacao;
            }

            @Override
            public T next() {
                lock.lock();
                try {
                    while (tabela[i] == null && i < tamanho) {
                        i++;
                    }

                    if (i >= tamanho) {
                        return null;
                    }

                    return tabela[i++].valor;
                } finally {
                    lock.unlock();
                }
            }
        };
    }
}