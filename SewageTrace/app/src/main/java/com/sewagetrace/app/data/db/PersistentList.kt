package com.sewagetrace.app.data.db

/**
 * 会「通知落库」的列表。
 *
 * DemoRepo 原来的 `mutableListOf` 被几十处代码直接 add / set / remove。
 * 用这个类替换后，调用方一行都不用改：任何写操作都会触发 [onChange]，
 * 由 [Store] 合并成一次后台写库。
 *
 * 不拦截的情况：通过 iterator()/listIterator() 删改、subList 视图写入。
 * 项目里没有这样用；新代码请用下面这些方法。
 */
class PersistentList<T>(
    private val inner: MutableList<T> = ArrayList(),
    private val onChange: () -> Unit
) : MutableList<T> by inner {

    /** 从数据库装载时用：替换全部内容，但不触发写库 */
    fun loadSilently(items: Collection<T>) {
        inner.clear()
        inner.addAll(items)
    }

    override fun add(element: T): Boolean = inner.add(element).also { onChange() }
    override fun add(index: Int, element: T) { inner.add(index, element); onChange() }
    override fun addAll(elements: Collection<T>): Boolean = inner.addAll(elements).also { if (it) onChange() }
    override fun addAll(index: Int, elements: Collection<T>): Boolean = inner.addAll(index, elements).also { if (it) onChange() }
    override fun set(index: Int, element: T): T = inner.set(index, element).also { onChange() }
    override fun remove(element: T): Boolean = inner.remove(element).also { if (it) onChange() }
    override fun removeAt(index: Int): T = inner.removeAt(index).also { onChange() }
    override fun removeAll(elements: Collection<T>): Boolean = inner.removeAll(elements).also { if (it) onChange() }
    override fun retainAll(elements: Collection<T>): Boolean = inner.retainAll(elements).also { if (it) onChange() }
    override fun clear() { inner.clear(); onChange() }

    override fun equals(other: Any?): Boolean = inner == other
    override fun hashCode(): Int = inner.hashCode()
    override fun toString(): String = inner.toString()
}
