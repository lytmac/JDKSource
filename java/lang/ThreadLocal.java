/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */

package java.lang;

import java.lang.ref.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对于多个线程并发的访问同一个变量，且修改的效果仅在本线程可见的场景，线程可以使用ThreadLocal来封闭一些局部变量，仅供该线程自身访问。
 * Thread对象关联了一个数组：ThreadLocalMap，用于存放这些局部变量。而每个局部变量都用一个ThreadLocal对象作为引用。
 * This class provides thread-local variables.
 * These variables differ from their normal counterparts in that each thread that accesses one (via its get or set method)
 * has its own, independently initialized copy of the variable.
 * 
 * ThreadLocal instances are typically private static fields in classes that wish to associate state with a thread (e.g., a user ID or Transaction ID).
 * 
 * For example, the class below generates unique identifiers local to each thread.
 * A thread's id is assigned the first time it invokes ThreadId.get() and remains unchanged on subsequent calls.
 * 
     * import java.util.concurrent.atomic.AtomicInteger;
     *
     * public class ThreadId {
     *      // Atomic integer containing the next thread ID to be assigned(全局的，多个线程共享的变量。)
     *      private static final AtomicInteger nextId = new AtomicInteger(0);
     *
     *      // Thread local variable containing each thread's ID
     *      private static final ThreadLocal<Integer> threadId = new ThreadLocal<Integer>() {
     *
     *          Override
     *          protected Integer initialValue() {
     *              return nextId.getAndIncrement();
     *          }
     *      };
     *
     *      // Returns the current thread's unique ID, assigning it if necessary
     *      public static int get() {
     *          return threadId.get();
     *      }
     * }
 *
 * 线程消失后，它的所有线程局部实例副本都要进行垃圾收集（除非存在对这些副本的其他引用）。
 * Each thread holds an implicit reference to its copy of a thread-local variable as long as the thread is alive and the ThreadLocal
 * instance is accessible; after a thread goes away, all of its copies of thread-local instances are subject to garbage collection
 * (unless other references to these copies exist).
 *
 * @author Josh Bloch and Doug Lea
 * @since 1.2
 */
public class ThreadLocal<T> {
    /**
     * 每个线程都维护了一个线性探测哈希表(开放式探址法)
     * ThreadLocals rely on per-thread linear-probe hash maps attached to each thread (Thread.threadLocals and inheritableThreadLocals).
     * The ThreadLocal objects act as keys, searched via threadLocalHashCode.
     * 
     * This is a custom hash code (useful only within ThreadLocalMaps) that eliminates collisions in the common case where consecutively
     * constructed ThreadLocals are used by the same threads, while remaining well-behaved in less common cases.
     */
    private final int threadLocalHashCode = nextHashCode();

    /**
     * The next hash code to be given out. Updated atomically. Starts at zero.
     */
    private static AtomicInteger nextHashCode = new AtomicInteger();

    /**
     * The difference between successively generated hash codes -
     * turns implicit sequential thread-local IDs into near-optimally spread multiplicative hash values for power-of-two-sized tables.
     */
    private static final int HASH_INCREMENT = 0x61c88647; //AtomicInteger的自增步长，近似最优扩展的乘法哈希值

    /**
     * Returns the next hash code.
     */
    private static int nextHashCode() {
        //每次向ThreadLocalMap中增加Entry，hashCode递增0x61c88647。这样能保证哈希码均匀的分布在2的N次方的数组里(神奇的数学)
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    /**
     * 线程在首次设置ThreadLocal变量时，可以重写该方法对ThreadLocal变量进行初始化
     * Returns the current thread's "initial value" for this thread-local variable.
     * This method will be invoked the first time a thread accesses the variable with the get method, unless the thread previously invoked the set
     * method, in which case the initialValue method will not be invoked for the thread.
     * Normally, this method is invoked at most once per thread, but it may be invoked again in case of subsequent invocations of remove followed by get.
     * 
     * This implementation simply returns null; if the programmer desires thread-local variables to have an initial value other than null,
     * ThreadLocal must be subclassed, and this method overridden. Typically, an anonymous inner class will be used.
     *
     * @return the initial value for this thread-local
     */
    protected T initialValue() {
        return null;
    }

    /**
     * Creates a thread local variable.
     */
    public ThreadLocal() {
    }

    /**
     * Returns the value in the current thread's copy of this thread-local variable.
     * If the variable has no value for the current thread, it is first initialized to the value returned by an invocation of the initialValue method.
     *
     * @return the current thread's value of this thread-local
     */
    public T get() {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null)
                return (T) e.value;
        }
        return setInitialValue();
    }

    /**
     * Variant of set() to establish initialValue.
     * Used instead of set() in case user has overridden the set() method.
     *
     * @return the initial value
     */
    private T setInitialValue() {
        T value = initialValue();
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
        return value;
    }

    /**
     * Sets the current thread's copy of this thread-local variable to the specified value.
     * Most subclasses will have no need to override this method, relying solely on the initialValue method to set the values of thread-locals.
     *
     * @param value the value to be stored in the current thread's copy of this thread-local.
     */
    public void set(T value) {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
    }

    /**
     * Removes the current thread's value for this thread-local variable.
     * If this thread-local variable is subsequently get by the current thread, its value will be reinitialized by invoking its initialValue method,
     * unless its value is set by the current thread in the interim.
     * This may result in multiple invocations of the initialValue method in the current thread.
     *
     * @since 1.5
     */
    public void remove() {
        ThreadLocalMap m = getMap(Thread.currentThread());
        if (m != null)
            m.remove(this);
    }

    /**
     * Get the map associated with a ThreadLocal. Overridden in InheritableThreadLocal.
     *
     * @param t the current thread
     * @return the map
     */
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }

    /**
     * Create the map associated with a ThreadLocal. Overridden in InheritableThreadLocal.
     *
     * @param t          the current thread
     * @param firstValue value for the initial entry of the map
     * @param map        the map to store.
     */
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    /**
     * Factory method to create map of inherited thread locals.
     * Designed to be called only from Thread constructor.
     *
     * @param parentMap the map associated with parent thread
     * @return a map containing the parent's inheritable bindings
     */
    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
        return new ThreadLocalMap(parentMap);
    }

    /**
     * Method childValue is visibly defined in subclass InheritableThreadLocal, but is internally defined here for the
     * sake of providing createInheritedMap factory method without needing to subclass the map class in InheritableThreadLocal.
     * This technique is preferable to the alternative of embedding instanceof tests in methods.
     */
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * ThreadLocalMap is a customized hash map suitable only for maintaining thread local values.
     * 对ThreadLocalMap的操作都封装在ThreadLocal内部。因为类是包私有的，所以允许在Thread内部声明字段
     * No operations are exported outside of the ThreadLocal class. The class is package private to allow declaration of fields in class Thread.
     * 
     * To help deal with very large and long-lived usages, the hash table entries use WeakReferences for keys.
     * 由于未使用referenceQueue，过时条目只有在表开始空间不足时才能保证删除。
     * However, since reference queues are not used, stale entries are guaranteed to be removed only when the table starts running out of space.
     */
    static class ThreadLocalMap {

        /**
         * The entries in this hash map extend WeakReference, using its main ref field as the key (which is always a ThreadLocal object).
         * Note that null keys (i.e. entry.get() == null) mean that the key is no longer referenced, so the entry can be expunged from table.
         * Such entries are referred to as "stale entries" in the code that follows.
         */
        static class Entry extends WeakReference<ThreadLocal> {
            // Entry继承WeakReference的意义在于当使用者使用完ThreadLocal对象后忘记释放掉该对象，也可以被GC回收。
            // 比如说某个ThreadLocal对象的生命周期已经完成了，但是数组还会继续引用该ThreadLocal对象直至该线程销毁。
            // 但是使用了WeakReference，在仅剩弱引用时GC就可以回收该对象。这样设计是尽最大的可能避免垃圾对象的堆积。
            /**
             * The value associated with this ThreadLocal.
             */
            Object value;

            Entry(ThreadLocal k, Object v) {
                super(k);
                value = v;
            }
        }

        /**
         * The initial capacity -- MUST be a power of two.
         */
        private static final int INITIAL_CAPACITY = 16; // AKA: 2^4，必须是2的倍数

        /**
         * The table, resized as necessary. table.length MUST always be a power of two.
         */
        private Entry[] table;

        /**
         * The number of entries in the table.
         */
        private int size = 0;

        /**
         * The next size value at which to resize.
         */
        private int threshold; // Default to 0

        /**
         * Set the resize threshold to maintain at worst a 2/3 load factor.
         */
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * Increment i modulo len. 下一个位置是计算位置的下一个，如果超过length了就从0重新开始
         */
        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }

        /**
         * Decrement i modulo len.
         */
        private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }

        /**
         * Construct a new map initially containing (firstKey, firstValue).
         * ThreadLocalMaps are constructed lazily, so we only create one when we have at least one entry to put in it.
         */
        ThreadLocalMap(ThreadLocal firstKey, Object firstValue) {
            table = new Entry[INITIAL_CAPACITY];
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            table[i] = new Entry(firstKey, firstValue);
            size = 1;
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * Construct a new map including all Inheritable ThreadLocals from given parent map. Called only by createInheritedMap.
         *
         * @param parentMap the map associated with parent thread.
         */
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new Entry[len];

            for (int j = 0; j < len; j++) {
                Entry e = parentTable[j];
                if (e != null) {
                    ThreadLocal key = e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        Entry c = new Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }

        /**
         * Get the entry associated with key.
         * This method itself handles only the fast path: a direct hit of existing key.
         * It otherwise relays to getEntryAfterMiss.
         * This is designed to maximize performance for direct hits, in part by making this method readily inlinable.
         *
         * @param key the thread local object
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntry(ThreadLocal key) {
            int i = key.threadLocalHashCode & (table.length - 1); //计算未出现冲突的情况下，Entry应该在table数组中的位置
            Entry e = table[i];
            if (e != null && e.get() == key)
                return e;
            else
                //未查询到
                return getEntryAfterMiss(key, i, e);
        }

        /**
         * Version of getEntry method for use when key is not found in its direct hash slot.
         *
         * @param key the thread local object
         * @param i   the table index for key's hash code
         * @param e   the entry at table[i]
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntryAfterMiss(ThreadLocal key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            /**
             * 通过计算的位置下标去找Entry,未匹配上则下标加1继续匹配。
             */
            while (e != null) {
                ThreadLocal k = e.get();
                if (k == key)
                    return e;
                if (k == null) //在遍历的途中可能扫到已经已经被GC掉的Entry
                    expungeStaleEntry(i);
                else
                    i = nextIndex(i, len);
                e = tab[i];
            }
            return null;
        }

        /**
         * Set the value associated with key.
         *
         * @param key   the thread local object
         * @param value the value to be set
         */
        private void set(ThreadLocal key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len - 1);

            for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {
                ThreadLocal k = e.get();

                if (k == key) {
                    e.value = value;
                    return;
                }

                if (k == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }

            tab[i] = new Entry(key, value);
            int sz = ++size;
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * Remove the entry for key.
         */
        private void remove(ThreadLocal key) {
            Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len - 1);
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                if (e.get() == key) {
                    e.clear();
                    expungeStaleEntry(i);
                    return;
                }
            }
        }

        /**
         * 移除过期的Entry(也就是已经被GC回收的Entry)。
         * Replace a stale entry encountered during a set operation with an entry for the specified key.
         * The value passed in the value parameter is stored in the entry, whether or not an entry already exists for the specified key.
         * 
         * As a side effect, this method expunges all stale entries in the "run" containing the stale entry.
         * (A run is a sequence of entries between two null slots.)
         *
         * @param key       the key
         * @param value     the value to be associated with key
         * @param staleSlot index of the first stale entry encountered while searching for key.
         */
        private void replaceStaleEntry(ThreadLocal key, Object value, int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;
            Entry e;

            // Back up to check for prior stale entry in current run.
            // We clean out whole runs at a time to avoid continual incremental rehashing due to garbage collector freeing up refs in bunches
            // (i.e., whenever the collector runs).
            int slotToExpunge = staleSlot;
            for (int i = prevIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = prevIndex(i, len))
                if (e.get() == null)
                    slotToExpunge = i;

            // Find either the key or trailing null slot of run, whichever occurs first
            for (int i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                ThreadLocal k = e.get();

                // If we find key, then we need to swap it with the stale entry to maintain hash table order.
                // The newly stale slot, or any other stale slot encountered above it, can then be sent to expungeStaleEntry to remove or rehash all of the other entries in run.
                if (k == key) {
                    e.value = value;

                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;

                    // Start expunge at preceding stale entry if it exists
                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }

                // If we didn't find stale entry on backward scan, the first stale entry seen while scanning for key is the first still present in the run.
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
            }

            // If key not found, put new entry in stale slot
            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);

            // If there are any other stale entries in run, expunge them
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

        /**
         * 删除已过时的Entry，只会被rehash()调用
         * Expunge a stale entry by rehashing any possibly colliding entries lying between staleSlot and the next null slot.
         * This also expunges any other stale entries encountered before the trailing null.(See Knuth, Section 6.4)
         *
         * @param staleSlot index of slot known to have null key
         * @return the index of the next null slot after staleSlot (all between staleSlot and this slot will have been checked for expunging).
         */
        private int expungeStaleEntry(int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;

            // expunge entry at staleSlot
            tab[staleSlot].value = null;
            tab[staleSlot] = null;
            size--;

            // Rehash until we encounter null
            Entry e;
            int i;
            for (i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
                ThreadLocal k = e.get();
                if (k == null) {
                    e.value = null;
                    tab[i] = null;
                    size--;
                } else {
                    int h = k.threadLocalHashCode & (len - 1);
                    if (h != i) {
                        tab[i] = null;

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.
                        while (tab[h] != null)
                            h = nextIndex(h, len);
                        tab[h] = e;
                    }
                }
            }
            return i;
        }

        /**
         * Heuristically scan some cells looking for stale entries.
         * This is invoked when either a new element is added, or another stale one has been expunged.
         * It performs a logarithmic number of scans, as a balance between no scanning (fast but retains garbage) and a number of scans
         * proportional to number of elements, that would find all garbage but would cause some insertions to take O(n) time.
         *
         * @param i a position known NOT to hold a stale entry. The scan starts at the element after i.
         * @param n scan control: log2(n) cells are scanned, unless a stale entry is found, in which case log2(table.length)-1 additional cells are scanned.
         *          When called from insertions, this parameter is the number of elements, but when from replaceStaleEntry, it is the table length.
         *          (Note: all this could be changed to be either more or less aggressive by weighting n instead of just using straight log n.
         *          But this version is simple, fast, and seems to work well.)
         * @return true if any stale entries have been removed.
         */
        private boolean cleanSomeSlots(int i, int n) {
            boolean removed = false;
            Entry[] tab = table;
            int len = tab.length;
            do {
                i = nextIndex(i, len);
                Entry e = tab[i];
                if (e != null && e.get() == null) {
                    n = len;
                    removed = true;
                    i = expungeStaleEntry(i);
                }
            } while ((n >>>= 1) != 0);
            return removed;
        }

        /**
         * Re-pack and/or re-size the table. First scan the entire table removing stale entries. If this doesn't sufficiently shrink the size of the table, double the table size.
         */
        private void rehash() {
            expungeStaleEntries(); //先清理掉所有已经被GC回收掉的Entry

            // Use lower threshold for doubling to avoid hysteresis
            if (size >= threshold - threshold / 4)
                resize();
        }

        /**
         * Double the capacity of the table.
         */
        private void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
            int newLen = oldLen * 2;
            Entry[] newTab = new Entry[newLen];
            int count = 0;

            for (int j = 0; j < oldLen; ++j) {
                Entry e = oldTab[j];
                if (e != null) {
                    ThreadLocal k = e.get();
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        int h = k.threadLocalHashCode & (newLen - 1);
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = e;
                        count++;
                    }
                }
            }

            setThreshold(newLen);
            size = count;
            table = newTab;
        }

        /**
         * Expunge all stale entries in the table.
         */
        private void expungeStaleEntries() {
            Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                Entry e = tab[j];
                if (e != null && e.get() == null)
                    expungeStaleEntry(j);
            }
        }
    }
}
