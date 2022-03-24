package linked_list_set;

import java.util.concurrent.atomic.AtomicMarkableReference;

public class SetImpl implements Set {
    private static class Node {
        AtomicMarkableReference<Node> next;
        int x;

        Node(int x, Node next) {
            this.next = new AtomicMarkableReference<>(next, false);
            this.x = x;
        }
    }

    private static class Window {
        Node cur, next;

        Window(Node cur, Node next) {
            this.cur = cur;
            this.next = next;
        }
    }

    private final Node head = new Node(Integer.MIN_VALUE, new Node(Integer.MAX_VALUE, null));

    /**
     * Returns the {@link Window}, where cur.x < x <= next.x
     */
    private Window findWindow(int x) {
        retry:
        while (true) {
            Node cur = head;
            Node next = cur.next.getReference();
            boolean[] removed = new boolean[1];
            while (next.x < x) {
                Node after = next.next.get(removed);
                if (removed[0]) {
                    if (!cur.next.compareAndSet(next, after, false, false))
                        continue retry;
                    next = after;
                } else {
                    cur = next;
                    next = cur.next.getReference();
                }
            }
            while (true) {
                Node after = next.next.get(removed);
                if (removed[0]) {
                    if (!cur.next.compareAndSet(next, after, false, false))
                        continue retry;
                    next = after;
                } else {
                    return new Window(cur, next);
                }
            }
        }
    }

    @Override
    public boolean add(int x) {
        while (true) {
            Window w = findWindow(x);
            if (w.next.x == x)
                return false;
            Node current = new Node(x, w.next);
            if (w.cur.next.compareAndSet(w.next, current, false, false))
                return true;
        }
    }

    @Override
    public boolean remove(int x) {
        while (true) {
            Window w = findWindow(x);
            if (w.next.x != x) {
                return false;
            } else {
                Node after = w.next.next.getReference();
                if (w.next.next.compareAndSet(after, after, false, true)) {
                    w.cur.next.compareAndSet(w.next, after, false, false);
                    return true;
                }
            }
        }
    }

    @Override
    public boolean contains(int x) {
        Window w = findWindow(x);
        return w.next.x == x;
    }
}