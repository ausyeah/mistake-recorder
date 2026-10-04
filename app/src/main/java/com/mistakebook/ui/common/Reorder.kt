package com.mistakebook.ui.common

internal fun <T> moveItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    return items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

internal fun <T> orderByIds(
    items: List<T>,
    order: List<Long>,
    idOf: (T) -> Long
): List<T> {
    if (order.isEmpty()) return items
    val rank = order.withIndex().associate { it.value to it.index }
    return items.withIndex()
        .sortedWith(compareBy<IndexedValue<T>> { rank[idOf(it.value)] ?: Int.MAX_VALUE }.thenBy { it.index })
        .map { it.value }
}

internal fun mergeVisibleOrder(existing: List<Long>, visibleOrder: List<Long>): List<Long> {
    val visible = visibleOrder.distinct()
    if (visible.isEmpty()) return existing
    val visibleSet = visible.toSet()
    val merged = existing.toMutableList()
    var nextVisible = 0
    for (index in merged.indices) {
        if (merged[index] in visibleSet && nextVisible < visible.size) {
            merged[index] = visible[nextVisible++]
        }
    }
    while (nextVisible < visible.size) merged.add(visible[nextVisible++])
    return merged
}
