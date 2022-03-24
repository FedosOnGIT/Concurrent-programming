package msqueue;

import kotlinx.atomicfu.AtomicRef;

import java.util.function.Function;
import java.util.function.Predicate;

public class MSQueue implements Queue {
    private final AtomicRef<Node> head;
    private final AtomicRef<Node> tail;

    public MSQueue() {
        Node dummy = new Node(0, null);
        this.head = new AtomicRef<>(dummy);
        this.tail = new AtomicRef<>(dummy);
    }


    @Override
    public void enqueue(int x) {
        Node newTail = new Node(x, null);
        while (true) {
            Node currentTail = tail.getValue();
            if (tail.getValue().next.compareAndSet(null, newTail)) {
                tail.compareAndSet(currentTail, newTail);
                return;
            } else {
                tail.compareAndSet(currentTail, currentTail.next.getValue());
            }
        }
    }

    private int takeElement(Predicate<Node> predicate) {
        while (true) {
            Node currentHead = head.getValue();
            Node currentTail = tail.getValue();
            if (currentTail.next.getValue() != null) {
                tail.compareAndSet(currentTail, currentTail.next.getValue());
            }
            if (currentHead == tail.getValue()) {
                if (tail.getValue().next.getValue() == null) {
                    return Integer.MIN_VALUE;
                }
                tail.compareAndSet(currentTail, currentTail.next.getValue());
            }
            if (predicate.test(currentHead)) {
                return currentHead.next.getValue().x;
            }
        }
    }

    @Override
    public int dequeue() {
        return takeElement(currentHead -> head.compareAndSet(currentHead, currentHead.next.getValue()));
    }

    @Override
    public int peek() {
        return takeElement(currentHead -> currentHead == head.getValue());
    }

    private static class Node {
        final int x;
        AtomicRef<Node> next;

        Node(int x, Node next) {
            this.x = x;
            this.next = new AtomicRef<>(next);
        }
    }
}
