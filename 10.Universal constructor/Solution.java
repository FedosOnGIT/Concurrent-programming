/**
 * @author : Надуткин Федор
 */
public class Solution implements AtomicCounter {
    // объявите здесь нужные вам поля

    final Node root = new Node(0);
    final ThreadLocal<Node> last;
    final ThreadLocal<Integer> my;

    public Solution() {
        last = ThreadLocal.withInitial(() -> root);
        my = ThreadLocal.withInitial(() -> 0);
    }

    public int getAndAdd(int x) {
        Node node = new Node(x);
        int res = 0;
        while (last.get() != node) {
            Node previous = last.get();
            Node help = previous.next.decide(node);
            last.set(help);
            res = my.get();
            my.set(my.get() + last.get().args);
        }
        return res;
    }

    // вам наверняка потребуется дополнительный класс
    private static class Node {
        public final int args;
        public final Consensus<Node> next;

        public Node(int args) {
            this.args = args;
            next = new Consensus<>();
        }
    }
}
