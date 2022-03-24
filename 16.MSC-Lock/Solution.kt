import java.util.concurrent.atomic.*

class Solution(val env: Environment) : Lock<Solution.Node> {
    // todo: необходимые поля (val, используем AtomicReference)
    val tail = AtomicReference<Node>(null)


    override fun lock(): Node {
        val my = Node() // сделали узел
        my.locked.compareAndSet(false, true)
        val pred = tail.getAndSet(my)
        if (pred != null) {
            pred.next.compareAndSet(null, my)
            while (my.locked.value) env.park()
        }
        return my // вернули узел
    }

    override fun unlock(node: Node) {
        if (node.next.value == null) {
            if (tail.compareAndSet(node, null))
                return
            while (node.next.value == null) {
                //pass
            }
        }
        node.next.value!!.locked.compareAndSet(true, false)
        env.unpark(node.next.value!!.thread)
    }

    class Node {
        val thread = Thread.currentThread() // запоминаем поток, которые создал узел
        // todo: необходимые поля (val, используем AtomicReference)
        val locked = AtomicReference(false)
        val next = AtomicReference<Node?>(null)
    }
}