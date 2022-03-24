import java.util.concurrent.atomic.AtomicMarkableReference
import kotlin.random.Random

class SkipListSet<E : Comparable<E>>(
    infinityLeft: E, val infinityRight: E
) {
    private val head: Node<E>

    init {
        head = Node(infinityLeft, MAX_LEVEL)
        val tail = Node(infinityRight, MAX_LEVEL)
        for (i in head.next.indices) head.next[i].compareAndSet(null, tail, false, false)
    }

    /**
     * Adds the specified [element] to this set if it is not already present.
     * Returns `true` if the [element] was not present, `false` otherwise.
     */
    fun add(element: E): Boolean {
        val topLevel = randomLevel()
        while (true) {
            var w = findWindow(element)
            if (w.found) return false
            val additional = Node(element, topLevel)
            for (level in 0..topLevel)
                additional.next[level].set(w.successors[level], false)
            var predecessor = w.predecessors[0]!!
            var successor = w.successors[0]!!
            additional.next[0].set(successor, false)
            if (!predecessor.next[0].compareAndSet(successor, additional, false, false)) continue
            for (level in 1..topLevel) {
                while (true) {
                    predecessor = w.predecessors[level]!!
                    successor = w.successors[level]!!
                    if (predecessor.next[level].compareAndSet(successor, additional, false, false))
                        break
                    w = findWindow(element)
                }
            }
            return true
        }
    }


    /**
     * Removes the specified [element] from this set.
     * Returns `true` if the [element] was presented in this set,
     * `false` otherwise.
     */
    fun remove(element: E): Boolean {
            val w = findWindow(element)
            if (!w.found) return false
            val removing = w.successors[0]!!
            val removed = BooleanArray(1)
            for (level in removing.topLevel downTo 1) {
                var successor = removing.next[level].get(removed)
                while (!removed[0]) {
                    removing.next[level].attemptMark(successor, true)
                    successor = removing.next[level].get(removed)
                }
            }
            while (true) {
                val successor = removing.next[0].get(removed)
                if (removing.next[0].compareAndSet(successor, successor, false, true)) {
                    findWindow(element)
                    return true
                } else if (removed[0]) return false
            }
    }

    /**
     * Returns `true` if this set contains the specified [element].
     */
    fun contains(element: E): Boolean {
        return findWindow(element).found
    }

    /**
     * Returns the [Window], where
     * `preds[l].x < x <= succs[l].x`
     * for every level `l`
     */
    private fun findWindow(element: E): Window<E> {
        val w = Window<E>()
        retry@
        while (true) {
            var predecessor = head
            var current = head
            for (level in MAX_LEVEL downTo 0) {
                current = predecessor.next[level].reference
                val removed = BooleanArray(1)
                while (true) {
                    var successor = current.next[level].get(removed)
                    while (removed[0]) {
                        if (!predecessor.next[level].compareAndSet(current, successor, false, false)) {
                            continue@retry
                        }
                        current = predecessor.next[level].reference
                        successor = current.next[level].get(removed)
                    }
                    if (current.element < element) {
                        predecessor = current
                        current = successor
                    } else
                        break
                }
                w.predecessors[level] = predecessor
                w.successors[level] = current
            }
            w.found = (current.element == element)
            return w
        }
    }
}

private class Node<E>(
    val element: E,
    val topLevel: Int
) {
    val next = Array(topLevel + 1) { AtomicMarkableReference<Node<E>>(null, false) }
}

private class Window<E>() {
    var predecessors = Array<Node<E>?>(MAX_LEVEL + 1) { null }
    var successors = Array<Node<E>?>(MAX_LEVEL + 1) { null }

    var found: Boolean = false
}

private fun randomLevel(): Int = Random.nextInt(MAX_LEVEL)

private const val MAX_LEVEL = 30
