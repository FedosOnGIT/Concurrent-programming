package stack;

import kotlinx.atomicfu.AtomicArray;
import kotlinx.atomicfu.AtomicRef;

import java.util.Random;

public class StackImpl implements Stack {
    private static class Node {
        final AtomicRef<Node> next;
        final int x;

        Node(int x, Node next) {
            this.next = new AtomicRef<>(next);
            this.x = x;
        }
    }

    // head pointer
    private final int SIZE = 15;
    private final Random RANDOM = new Random();
    private final AtomicRef<Node> head = new AtomicRef<>(null);
    private final AtomicArray<Node> elimination = new AtomicArray<>(SIZE);

    private int getPosition(int index) {
        return (index + SIZE) % SIZE;
    }

    @Override
    public void push(int x) {
        Node input = new Node(x, null);
        int index = RANDOM.nextInt() % SIZE;
        for (int i = 0; i <= SIZE; i++) {
            int position = getPosition(index + i);
            if (elimination.get(position).compareAndSet(null, input)) {
                for (int j = 0; j < 10; j++) {
                    if (elimination.get(position).getValue() == null) {
                        return;
                    }
                }
                if (!elimination.get(position).compareAndSet(input, null)) {
                    return;
                }
                break;
            }
        }
        while (true) {
            Node expected = head.getValue();
            Node update = new Node(x, expected);
            if (head.compareAndSet(expected, update)) {
                return;
            }
        }
    }

    @Override
    public int pop() {
        int index = RANDOM.nextInt() % SIZE;
        for (int i = 0; i <= SIZE; i++) {
            int position = getPosition(index + i);
            Node value = elimination.get(position).getValue();
            if (value != null) {
                if (elimination.get(position).compareAndSet(value, null)) {
                    return value.x;
                }
            }
        }
        while (true) {
            Node current = head.getValue();
            if (current == null) return Integer.MIN_VALUE;
            if (head.compareAndSet(current, current.next.getValue())) {
                return current.x;
            }
        }
    }
}
