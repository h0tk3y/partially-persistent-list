import java.util.*

class NodeHandle<T>(val version: Int,
                    val nodeIsPresent: Boolean)

fun <T> partlyPersistentListOf(vararg values: T) = PartiallyPersistentList<T>(values.asIterable())

class PartiallyPersistentList<T>() : Iterable<T> {
    constructor(from: Iterable<T>) : this() {
        val iterator = from.iterator()
        if (!iterator.hasNext()) {
            return
        } else {
            ++version
            val newNode = Node(iterator.next(), nextNodeId(), null, null, version, null)
            var current = newNode
            while (iterator.hasNext()) {
                val next = Node(iterator.next(), nextNodeId(), current, null, version, null)
                current.next = next
                current = next
            }
            headsOrdered[version] = newNode
            head = newNode
            tail = current
        }
    }

    private inner class Node(val value: T,
                             val id: Int,
                             var prev: Node?,
                             var next: Node?,
                             var since: Int,
                             var twin: Node?) {
        init {
            nodeById[id] = this
        }

        fun actual() = when {
            twin == null -> this
            (twin?.since?.compareTo(since) ?: 0) > 0 -> twin!!
            else -> this
        }
    }

    var version: Int = 0; private set
    private val nodeByHandle = WeakHashMap<NodeHandle<T>, Node>()

    private var lastNodeId: Int = 0; private set
    private fun nextNodeId() = lastNodeId++
    private val nodeById = HashMap<Int, Node>()

    private fun newHandle(node: Node, version: Int = this.version) =
            NodeHandle<T>(version, true).apply { nodeByHandle[this] = node }

    private var head: Node? = null
    private var tail: Node? = null
    private val headsOrdered = TreeMap<Int, Node>()

    private tailrec fun updateToLeft(n: Node, newNext: Node?) {
        val newNode = Node(n.value, n.id, n.prev, newNext, version, if (n.twin == null) n else null)
        newNext?.prev = newNode

        updateHeadTail(n, newNode)

        if (n.twin == null)
            n.twin = newNode
        else if (n.prev != null)
            updateToLeft(n.prev!!.actual(), newNode)
    }

    private tailrec fun updateToRight(n: Node, newPrev: Node?) {
        val newNode = Node(n.value, n.id, newPrev, n.next, version, if (n.twin == null) n else null)
        newPrev?.next = newNode

        if (tail == n) {
            tail = newNode
        }

        if (n.twin == null)
            n.twin = newNode
        else if (n.next != null)
            updateToRight(n.next!!.actual(), newNode)
    }

    fun canMutate(nodeHandle: NodeHandle<T>) = nodeHandle.version == version

    private fun checkCanMutate(nodeHandle: NodeHandle<T>) {
        check(canMutate(nodeHandle)) { "Node for this handle cannot be mutated anymore." }
    }

    fun add(value: T): NodeHandle<T> {
        ++version

        val newNode = Node(value, nextNodeId(), tail, null, version, null)
        tail = newNode
        if (head == null) {
            head = head ?: newNode
            headsOrdered[newNode.since] = newNode
        }

        newNode.prev?.let { updateToLeft(it, newNode) }

        return newHandle(newNode)
    }

    fun addAfter(nodeHandle: NodeHandle<T>, value: T): NodeHandle<T> {
        checkCanMutate(nodeHandle)
        val node = nodeByHandle(nodeHandle)
        ++version

        val newNode = Node(value, nextNodeId(), node.actual(), node.next?.actual(), version, null)
        newNode.next?.let { updateToRight(it, newNode) }
        newNode.prev?.let { updateToLeft(it, newNode) }
        return newHandle(newNode)
    }

    fun addFirst(value: T): NodeHandle<T> {
        ++version

        val newNode = Node(value, nextNodeId(), null, head, version, null)
        head = newNode
        headsOrdered[newNode.since] = newNode
        tail = tail ?: newNode
        newNode.next?.let { updateToRight(it, newNode) }

        return newHandle(newNode)
    }

    operator fun set(nodeHandle: NodeHandle<T>, value: T): NodeHandle<T> {
        checkCanMutate(nodeHandle)

        val node = nodeByHandle(nodeHandle)
        ++version

        val newNode = Node(value, node.id, node.prev?.actual(), node.next?.actual(), version, if (node.twin == null) node else null)

        if (node.twin == null) {
            node.twin = newNode
            updateHeadTail(node, newNode)
        } else {
            node.prev?.let { updateToLeft(it, newNode) }
            node.next?.let { updateToRight(it, newNode) }
        }

        return newHandle(newNode)
    }

    private fun updateHeadTail(node: Node, newNode: Node) {
        if (head == node) {
            head = newNode
            headsOrdered[newNode.since] = newNode
        }
        if (tail == node) {
            tail = newNode
        }
    }

    fun remove(nodeHandle: NodeHandle<T>): NodeHandle<T> {
        checkCanMutate(nodeHandle)
        val node = nodeByHandle(nodeHandle)

        ++version

        val fakeNode = Node(node.value, -1, null, null, 0, null)
        node.prev?.let { updateToLeft(it.actual(), fakeNode) }
        val newPrev = fakeNode.prev
        node.next?.let { updateToRight(it.actual(), fakeNode) }
        val newNext = fakeNode.next

        newPrev?.next = newNext
        newNext?.prev = newPrev

        nodeById.remove(node.id)

        return if (newNext == null)
            NodeHandle(version, false) else
            newHandle(newNext)
    }

    private inner class VersionedIterator(val iteratorVersion: Int,
                                          var next: Node? = headsOrdered.floorEntry(iteratorVersion)?.value) : Iterator<T> {
        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            val result = next!!.value
            next = chooseNext(iteratorVersion, next!!)
            return result
        }

        override fun hasNext(): Boolean = next != null

        private fun chooseNext(version: Int, n: Node): Node? {
            val n1 = n.next?.actual()
            val n2 = n1?.twin
            return when {
                n1 == null -> null
                n2 == null -> n1
                n1.since > version -> if (n2.since > version) null else n2
                else -> n1
            }
        }
    }

    fun actualHandleFor(nodeHandle: NodeHandle<T>, version: Int = this.version): NodeHandle<T> {
        val node = nodeByHandle(nodeHandle)
        if (node.since == version || version == this.version && node.twin == null)
            return nodeHandle
        if (version == this.version) {
            val result = nodeById[node.id] ?: return NodeHandle(version, false)
            return newHandle(result)
        }
        val iterator = versionIterator(version) as VersionedIterator
        val id = node.id
        while (iterator.hasNext()) {
            if (iterator.next?.id == id)
                return newHandle(iterator.next!!, version)
            iterator.next()
        }
        return NodeHandle(version, false)
    }

    private fun nodeByHandle(nodeHandle: NodeHandle<T>) =
            nodeByHandle[nodeHandle] ?: throw IllegalArgumentException("Node handle is unknown to this list.")

    fun find(value: T, version: Int = this.version): NodeHandle<T> {
        val iterator = versionIterator(version) as VersionedIterator
        while (iterator.hasNext()) {
            val v = iterator.next!!.value
            if (v == value)
                return newHandle(iterator.next!!, version)
            iterator.next()
        }
        return NodeHandle(version, false)
    }

    fun versionIterator(version: Int): Iterator<T> {
        require(version in 0..this.version) { "Version $version is out of versions bounds [0..${this.version}]" }
        return VersionedIterator(version)
    }

    override fun iterator(): Iterator<T> = versionIterator(version)

    fun iteratorAt(nodeHandle: NodeHandle<T>): Iterator<T> {
        val node = nodeByHandle(nodeHandle)
        return VersionedIterator(nodeHandle.version, node)
    }
}

fun <T> PartiallyPersistentList<T>.snapshot(version: Int = this.version) = versionIterator(version).asSequence().toList()