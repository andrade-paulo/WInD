package com.wind.message.Huffman;

// Lista de prioridades para a árvore de Huffman
public class HeapHuffman implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private NodeHuffman[] heap;
    private int capacidade;
    private int tamanho;

    public HeapHuffman(int n) {
        this.capacidade = n;
        this.heap = new NodeHuffman[n];
        this.tamanho = 0;
    }

    private int indicePai(int i) {
        return (i - 1) / 2;
    }

    private int indiceFilhoEsquerdo(int i) {
        return 2 * i + 1;
    }

    private int indiceFilhoDireito(int i) {
        return 2 * i + 2;
    }

    public void inserir(NodeHuffman node) {
        if (tamanho == capacidade) {
            throw new IllegalStateException("Heap cheio");
        }

        heap[tamanho] = node;
        int i = tamanho;
        tamanho++;

        while (i != 0 && heap[indicePai(i)].freq > heap[i].freq) {
            trocar(i, indicePai(i));
            i = indicePai(i);
        }
    }

    public NodeHuffman removerMinimo() {
        if (tamanho == 0) {
            System.out.println("Lista vazia. Nao da pra remover nada.");
            return null;
        }

        NodeHuffman raiz = heap[0];
        NodeHuffman ultimoElemento = heap[tamanho - 1]; 

        heap[0] = ultimoElemento;  
        tamanho--;

        heapify(0); 
        return raiz;
    }

    private void heapify(int i) {
        int menor = i;
        int esquerdo = indiceFilhoEsquerdo(i);
        int direito = indiceFilhoDireito(i);

        if (esquerdo < tamanho && heap[esquerdo].freq < heap[menor].freq) {
            menor = esquerdo;
        }

        if (direito < tamanho && heap[direito].freq < heap[menor].freq) {
            menor = direito;
        }

        if (menor != i) {
            trocar(i, menor);
            heapify(menor);
        }
    }

    private void trocar(int i, int j) {
        NodeHuffman temp = heap[i];
        heap[i] = heap[j];
        heap[j] = temp;
    }

    public void imprimirHeap() {
        for (int i = 0; i < tamanho; i++) {
            System.out.println(heap[i]);
        }
    }

    public int tamanho() {
        return this.tamanho;
    }
}