
/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.util.concurrent.locks.*;
import java.util.*;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;

/**
 * ConcurrentHashMap是支持完全并发的读
 * A hash table supporting full concurrency of retrievals and adjustable expected concurrency for updates.
 * This class obeys the same functional specification as java.util.Hashtable, and includes versions of methods corresponding to each method of Hashtable.
 * However, even though all operations are thread-safe, retrieval operations do not entail locking, and there is not any support for locking the entire table in a way that prevents all access.
 * 
 * This class is fully interoperable with Hashtable in programs that rely on its thread safety but not on its synchronization details.
 * 
 * Retrieval operations (including get) generally do not block, so may overlap with update operations (including put and remove).
 * Retrievals reflect the results of the most recently completed update operations holding upon their onset. For aggregate operations such as putAll and
 * clear, concurrent retrievals may reflect insertion or removal of only some entries. Similarly, Iterators and Enumerations return elements reflecting
 * the state of the hash table at some point at or since the creation of the iterator/enumeration. They do not throw  ConcurrentModificationException.
 * However, iterators are designed to be used by only one thread at a time.
 * 
 * The allowed concurrency among update operations is guided by the optional concurrencyLevel constructor argument (default 16), which is used as a hint
 * for internal sizing. The table is internally partitioned to try to permit the indicated number of concurrent updates without contention. Because placement
 * in hash tables is essentially random, the actual concurrency will vary. Ideally, you should choose a value to accommodate as many threads as will ever
 * concurrently modify the table. Using a significantly higher value than you need can waste space and time, and a significantly lower value can lead to
 * thread contention. But overestimates and underestimates within an order of magnitude do not usually have much noticeable impact. A value of one is
 * appropriate when it is known that only one thread will modify and all others will only read. Also, resizing this or any other kind of hash table is
 * a relatively slow operation, so, when possible, it is a good idea to provide estimates of expected table sizes in constructors.
 * 
 * This class and its views and iterators implement all of the optional methods of the  Map and Iterator interfaces.
 * 
 * Like Hashtable but unlike HashMap, this class does not allow null to be used as a key or value.
 * 
 * This class is a member of the Java Collections Framework.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @author Doug Lea
 * @since 1.5
 */
public class ConcurrentHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, Serializable {
    private static final long serialVersionUID = 7249069246763182397L;

    /*
     * The basic strategy is to subdivide the table among Segments, each of which itself is a concurrently readable hash table.
     * To reduce footprint, all but one segments are constructed only when first needed (see ensureSegment). To maintain visibility
     * in the presence of lazy construction, accesses to segments as well as elements of segment's table must use volatile access,
     * which is done via Unsafe within methods segmentAt etc below. These provide the functionality of AtomicReferenceArrays
     * but reduce the levels of indirection. Additionally, volatile-writes of table elements and entry "next" fields within locked
     * operations use the cheaper "lazySet" forms of writes (via putOrderedObject) because these writes are always
     * followed by lock releases that maintain sequential consistency of table updates.
     *
     * Historical note: The previous version of this class relied heavily on "final" fields, which avoided some volatile reads at
     * the expense of a large initial footprint. Some remnants of that design (including forced construction of segment 0) exist
     * to ensure serialization compatibility.
     */

    /* ---------------- Constants -------------- */

    /**
     * The default initial capacity for this table,
     * used when not otherwise specified in a constructor.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The default load factor for this table, used when not
     * otherwise specified in a constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The default concurrency level for this table, used when not
     * otherwise specified in a constructor.
     */
    static final int DEFAULT_CONCURRENCY_LEVEL = 16; //默认segment的个数是16

    /**
     * The maximum capacity, used if a higher value is implicitly
     * specified by either of the constructors with arguments.  MUST
     * be a power of two <= 1<<30 to ensure that entries are indexable
     * using ints.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The minimum capacity for per-segment tables.  Must be a power
     * of two, at least two to avoid immediate resizing on next use
     * after lazy construction.
     */
    static final int MIN_SEGMENT_TABLE_CAPACITY = 2;

    /**
     * The maximum number of segments to allow; used to bound
     * constructor arguments. Must be power of two less than 1 << 24.
     */
    static final int MAX_SEGMENTS = 1 << 16; // slightly conservative 2^16. segment的最大个数不能超过2^16

    /**
     * Number of unsynchronized retries in size and containsValue methods before resorting to locking. This is used to avoid
     * unbounded retries if tables undergo continuous modification which would make it impossible to obtain an accurate result.
     */
    static final int RETRIES_BEFORE_LOCK = 2;

    /* ---------------- Fields -------------- */

    /**
     * holds values which can't be initialized until after VM is booted.
     */
    private static class Holder {

        /**
         * Enable alternative hashing of String keys?
         * 
         * Unlike the other hash map implementations we do not implement a threshold for regulating whether alternative hashing is used for String keys.
         * Alternative hashing is either enabled for all instances or disabled for all instances.
         */
        static final boolean ALTERNATIVE_HASHING;

        static {
            // Use the "threshold" system property even though our threshold
            // behaviour is "ON" or "OFF".
            String altThreshold = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction("jdk.map.althashing.threshold"));

            int threshold;
            try {
                threshold = (null != altThreshold)
                        ? Integer.parseInt(altThreshold)
                        : Integer.MAX_VALUE;

                // disable alternative hashing if -1
                if (threshold == -1) {
                    threshold = Integer.MAX_VALUE;
                }

                if (threshold < 0) {
                    throw new IllegalArgumentException("value must be positive integer.");
                }
            } catch (IllegalArgumentException failed) {
                throw new Error("Illegal value for 'jdk.map.althashing.threshold'", failed);
            }
            ALTERNATIVE_HASHING = threshold <= MAXIMUM_CAPACITY;
        }
    }

    /**
     * A randomizing value associated with this instance that is applied to
     * hash code of keys to make hash collisions harder to find.
     */
    private transient final int hashSeed = randomHashSeed(this);

    private static int randomHashSeed(ConcurrentHashMap instance) {
        if (sun.misc.VM.isBooted() && Holder.ALTERNATIVE_HASHING) {
            return sun.misc.Hashing.randomHashSeed(instance);
        }

        return 0;
    }

    /**
     * Mask value for indexing into segments. The upper bits of a
     * key's hash code are used to choose the segment.
     */
    final int segmentMask;  // 用于定位段，大小等于segments.length - 1，是不可变的(final)

    /**
     * Shift value for indexing within segments.
     */
    final int segmentShift; // 用于定位段，大小等于32(hash值的位数)减去对segments的大小取以2为底的对数值，是不可变的(final)

    /**
     * The segments, each of which is a specialized hash table.
     */
    final Segment<K, V>[] segments; //注意final修饰符，表明segments在ConcurrentHashMap实例的整个生命周期中是不会改变的。所以扩容是不涉及到Segment的

    transient Set<Map.Entry<K, V>> entrySet;
    transient Set<K> keySet;
    transient Collection<V> values;

    /**
     * ConcurrentHashMap list entry. Note that this is never exported out as a user-visible Map.Entry.
     */
    static final class HashEntry<K, V> {
        final int hash;  //因为hash值一旦计算出来，到其生命周期完成都不会再变了
        final K key;
        volatile V value; //为了保证value再多线程访问的可见性
        volatile HashEntry<K, V> next;

        HashEntry(int hash, K key, V value, HashEntry<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        /**
         * Sets next field with volatile write semantics. (See above about use of putOrderedObject.)
         */
        final void setNext(HashEntry<K, V> n) {
            UNSAFE.putOrderedObject(this, nextOffset, n);
        }

        // Unsafe mechanics
        static final sun.misc.Unsafe UNSAFE;
        static final long nextOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class k = HashEntry.class;
                nextOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("next"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * Gets the ith element of given table (if nonnull) with volatile
     * read semantics. Note: This is manually integrated into a few performance-sensitive methods to reduce call overhead.
     */
    @SuppressWarnings("unchecked")
    static final <K, V> HashEntry<K, V> entryAt(HashEntry<K, V>[] tab, int i) {
        return (tab == null) ? null : (HashEntry<K, V>) UNSAFE.getObjectVolatile(tab, ((long) i << TSHIFT) + TBASE);
    }

    /**
     * Sets the ith element of given table, with volatile write semantics. (See above about use of putOrderedObject.)
     */
    static final <K, V> void setEntryAt(HashEntry<K, V>[] tab, int i, HashEntry<K, V> e) {
        UNSAFE.putOrderedObject(tab, ((long) i << TSHIFT) + TBASE, e);
    }

    /**
     * key计算出的原生的hashCode出现hash碰撞的可能性还是很大的，所以需要一个扰动函数来做一次调整。
     * Applies a supplemental hash function to a given hashCode, which defends against poor quality hash functions.
     * This is critical because ConcurrentHashMap uses power-of-two length hash tables, (table的长度是2的倍数，这一点很重要。)
     * that otherwise encounter collisions for hashCodes that do not differ in lower or upper bits.
     */
    private int hash(Object k) {
        int h = hashSeed;

        if ((0 != h) && (k instanceof String)) {
            return sun.misc.Hashing.stringHash32((String) k);
        }

        h ^= k.hashCode();

        // Spread bits to regularize both segment and index locations, using variant of single-word Wang/Jenkins hash.
        h += (h << 15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h << 3);
        h ^= (h >>> 6);
        h += (h << 2) + (h << 14);
        return h ^ (h >>> 16);
    }

    /**
     * Segments are specialized versions of hash tables.
     * This subclasses from ReentrantLock opportunistically, just to simplify some locking and avoid separate construction.
     */
    static final class Segment<K, V> extends ReentrantLock implements Serializable {
        /*
         * Segments maintain a table of entry lists that are always kept in a consistent state, so can be read (via volatile reads of segments and tables) without locking.
         * This requires replicating nodes when necessary during table resizing, so the old lists can be traversed by readers still using old version of table.
         *
         * This class defines only mutative methods requiring locking. Except as noted, the methods of this class perform the per-segment versions of ConcurrentHashMap methods.
         * (Other methods are integrated directly into ConcurrentHashMap methods.) These mutative methods use a form of controlled spinning on contention via methods scanAndLock and
         * scanAndLockForPut. These intersperse tryLocks with traversals to locate nodes. The main benefit is to absorb cache misses (which are very common for hash tables)
         * while obtaining locks so that traversal is faster once acquired. We do not actually use the found nodes since they must be re-acquired under lock anyway to ensure sequential
         * consistency of updates (and in any case may be undetectably stale), but they will normally be much faster to re-locate.
         * Also, scanAndLockForPut speculatively creates a fresh node to use in put if no node is found.
         */
        private static final long serialVersionUID = 2249069246763182397L;

        /**
         * The maximum number of times to tryLock in a prescan before possibly blocking on acquire in preparation for a locked segment operation.
         * On multiprocessors, using a bounded number of retries maintains cache acquired while locating nodes.
         */
        static final int MAX_SCAN_RETRIES = Runtime.getRuntime().availableProcessors() > 1 ? 64 : 1;

        /**
         * The per-segment table. Elements are accessed via entryAt/setEntryAt providing volatile semantics.
         */
        //分段的桶。声明为volatile是保证无锁读的关键
        transient volatile HashEntry<K, V>[] table;

        /**
         * The number of elements. Accessed only either within locks or among other volatile reads that maintain visibility.
         */
        //统计的是每个Segment管理的Entry的数量。
        //不在ConcurrentHashMap中使用全局的计数器是为了避免出现热点域而影响并发性。因为当需要更新计数器时，不用锁定整个ConcurrentHashMap
        transient int count;

        /**
         * The total number of mutative operations in this segment.
         * Even though this may overflows 32 bits, it provides sufficient accuracy for stability checks in CHM isEmpty() and size() methods.
         * Accessed only either within locks or among other volatile reads that maintain visibility.
         */
        transient int modCount; //用于统计段结构改变的次数，主要是为了检测对多个段进行遍历过程中某个段是否发生改变

        /**
         * The table is rehashed when its size exceeds this threshold. (The value of this field is always (int)(capacity * loadFactor).)
         */
        transient int threshold;

        /**
         * The load factor for the hash table.  Even though this value is same for all segments, it is replicated to avoid needing links to outer object.
         *
         * @serial
         */
        final float loadFactor;

        Segment(float lf, int threshold, HashEntry<K, V>[] tab) {
            this.loadFactor = lf;
            this.threshold = threshold;
            this.table = tab;
        }

        final V put(K key, int hash, V value, boolean onlyIfAbsent) {

            //如果没有拿到锁，则
            HashEntry<K, V> node = tryLock() ? null : scanAndLockForPut(key, hash, value);

            V oldValue;

            try {
                HashEntry<K, V>[] tab = table; // this.table：获取segment对应的table
                int index = (tab.length - 1) & hash;
                HashEntry<K, V> first = entryAt(tab, index); //获取table数组中指定下标的HashEntry对象
                for (HashEntry<K, V> e = first; ; ) { //依次遍历链表上的所有节点
                    if (e != null) {
                        K k;
                        if ((k = e.key) == key || (e.hash == hash && key.equals(k))) {
                            oldValue = e.value;
                            if (!onlyIfAbsent) { //onlyIfAbsent == true, 则直接返回; onlyIfAbsent == false, 则替换掉原有的<key, value>
                                e.value = value;
                                ++modCount;
                            }
                            break;
                        }
                        e = e.next;
                    } else { //链表为空或已经走到链表尾部,还没有找到已存在该key的节点。走到这里就可以插入新节点了
                        if (node != null)
                            node.setNext(first);
                        else
                            node = new HashEntry<K, V>(hash, key, value, first);

                        int c = count + 1;

                        if (c > threshold && tab.length < MAXIMUM_CAPACITY)
                            rehash(node);
                        else
                            setEntryAt(tab, index, node);

                        ++modCount;
                        count = c;
                        oldValue = null;
                        break;
                    }
                }
            } finally {
                unlock();
            }
            return oldValue;
        }

        /**
         * Doubles size of table and repacks entries, also adding the given node to new table
         */
        @SuppressWarnings("unchecked")
        private void rehash(HashEntry<K, V> node) {
            /*
             * Reclassify nodes in each list to new table. Because we are using power-of-two expansion, the elements from
             * each bin must either stay at same index, or move with a power of two offset. We eliminate unnecessary node
             * creation by catching cases where old nodes can be reused because their next fields won't change.
             *
             * Statistically, at the default threshold, only about one-sixth of them need cloning when a table doubles.
             * The nodes they replace will be garbage collectable as soon as they are no longer referenced by any reader
             * thread that may be in the midst of concurrently traversing table. Entry accesses use plain array indexing
             * because they are followed by volatile table write.
             */
            HashEntry<K, V>[] oldTable = table;
            int oldCapacity = oldTable.length;
            int newCapacity = oldCapacity << 1;
            threshold = (int) (newCapacity * loadFactor);
            HashEntry<K, V>[] newTable = (HashEntry<K, V>[]) new HashEntry[newCapacity];
            int sizeMask = newCapacity - 1;
            for (int i = 0; i < oldCapacity; i++) {
                HashEntry<K, V> e = oldTable[i];
                if (e != null) {
                    HashEntry<K, V> next = e.next;
                    int idx = e.hash & sizeMask;
                    if (next == null)   //  Single node on list
                        newTable[idx] = e;
                    else { // Reuse consecutive sequence at same slot
                        HashEntry<K, V> lastRun = e;
                        int lastIdx = idx;
                        for (HashEntry<K, V> last = next;
                             last != null;
                             last = last.next) {
                            int k = last.hash & sizeMask;
                            if (k != lastIdx) {
                                lastIdx = k;
                                lastRun = last;
                            }
                        }
                        newTable[lastIdx] = lastRun;
                        // Clone remaining nodes
                        for (HashEntry<K, V> p = e; p != lastRun; p = p.next) {
                            V v = p.value;
                            int h = p.hash;
                            int k = h & sizeMask;
                            HashEntry<K, V> n = newTable[k];
                            newTable[k] = new HashEntry<K, V>(h, p.key, v, n);
                        }
                    }
                }
            }
            int nodeIndex = node.hash & sizeMask; // add the new node
            node.setNext(newTable[nodeIndex]);
            newTable[nodeIndex] = node;
            table = newTable;
        }

        /**
         * Scans for a node containing given key while trying to acquire lock, creating and returning one if not found.
         * Upon return, guarantees that lock is held. (返回前一定要拿到锁)
         *
         * UNlike in most methods, calls to method equals are not screened:
         * Since traversal speed doesn't matter, we might as well help warm up the associated code and accesses as well.
         *
         * @return a new node if key not found, else null
         */
        private HashEntry<K, V> scanAndLockForPut(K key, int hash, V value) {
            HashEntry<K, V> first = entryForHash(this, hash);
            HashEntry<K, V> e = first;
            HashEntry<K, V> node = null;
            int retries = -1; // negative while locating node

            /**
             * 退出这个循环的途径有两个：1. tryLock()拿到锁; 2. 自旋MAX_SCAN_RETRIES次后调用lock()阻塞式拿锁
             */
            while (!tryLock()) {
                HashEntry<K, V> f; // to recheck first below
                if (retries < 0) { //首次进入while循环

                    if (e == null) { //链表都不存在，抢锁说明有其他线程在修改这个Entry
                        if (node == null) // speculatively create node
                            node = new HashEntry<K, V>(hash, key, value, null);
                        retries = 0;
                    } else if (key.equals(e.key)) //元素匹配上
                        retries = 0;
                    else //链表存在，继续遍历
                        e = e.next;

                } else if (++retries > MAX_SCAN_RETRIES) { //自旋MAX_SCAN_RETRIES次后，主动去阻塞式拿锁
                    lock(); //走到这里就说明线程间的竞争很激烈，继续自旋占用资源已经没有意义了，释放等就是了。
                    break;
                } else if ((retries & 1) == 0 && (f = entryForHash(this, hash)) != first) { //已经看到其他线程的写操作结果了
                    e = first = f; // re-traverse if entry changed
                    retries = -1;
                }
            }

            return node;
        }

        /**
         * Scans for a node containing the given key while trying to
         * acquire lock for a remove or replace operation. Upon
         * return, guarantees that lock is held.  Note that we must
         * lock even if the key is not found, to ensure sequential
         * consistency of updates.
         */
        private void scanAndLock(Object key, int hash) {
            // similar to but simpler than scanAndLockForPut
            HashEntry<K, V> first = entryForHash(this, hash);
            HashEntry<K, V> e = first;
            int retries = -1;
            while (!tryLock()) {
                HashEntry<K, V> f;
                if (retries < 0) {
                    if (e == null || key.equals(e.key))
                        retries = 0;
                    else
                        e = e.next;
                } else if (++retries > MAX_SCAN_RETRIES) {
                    lock();
                    break;
                } else if ((retries & 1) == 0 &&
                        (f = entryForHash(this, hash)) != first) {
                    e = first = f;
                    retries = -1;
                }
            }
        }

        /**
         * Remove; match on key only if value null, else match both.
         */
        final V remove(Object key, int hash, Object value) {
            if (!tryLock())
                scanAndLock(key, hash);
            V oldValue = null;
            try {
                HashEntry<K, V>[] tab = table;
                int index = (tab.length - 1) & hash;
                HashEntry<K, V> e = entryAt(tab, index);
                HashEntry<K, V> pred = null;
                while (e != null) {
                    K k;
                    HashEntry<K, V> next = e.next;
                    if ((k = e.key) == key ||
                            (e.hash == hash && key.equals(k))) {
                        V v = e.value;
                        if (value == null || value == v || value.equals(v)) {
                            if (pred == null)
                                setEntryAt(tab, index, next);
                            else
                                pred.setNext(next);
                            ++modCount;
                            --count;
                            oldValue = v;
                        }
                        break;
                    }
                    pred = e;
                    e = next;
                }
            } finally {
                unlock();
            }
            return oldValue;
        }

        final boolean replace(K key, int hash, V oldValue, V newValue) {
            if (!tryLock())
                scanAndLock(key, hash);
            boolean replaced = false;
            try {
                HashEntry<K, V> e;
                for (e = entryForHash(this, hash); e != null; e = e.next) {
                    K k;
                    if ((k = e.key) == key ||
                            (e.hash == hash && key.equals(k))) {
                        if (oldValue.equals(e.value)) {
                            e.value = newValue;
                            ++modCount;
                            replaced = true;
                        }
                        break;
                    }
                }
            } finally {
                unlock();
            }
            return replaced;
        }

        final V replace(K key, int hash, V value) {
            if (!tryLock())
                scanAndLock(key, hash);
            V oldValue = null;
            try {
                HashEntry<K, V> e;
                for (e = entryForHash(this, hash); e != null; e = e.next) {
                    K k;
                    if ((k = e.key) == key ||
                            (e.hash == hash && key.equals(k))) {
                        oldValue = e.value;
                        e.value = value;
                        ++modCount;
                        break;
                    }
                }
            } finally {
                unlock();
            }
            return oldValue;
        }

        final void clear() {
            lock();
            try {
                HashEntry<K, V>[] tab = table;
                for (int i = 0; i < tab.length; i++)
                    setEntryAt(tab, i, null);
                ++modCount;
                count = 0;
            } finally {
                unlock();
            }
        }
    }

    // Accessing segments

    /**
     * 通过Unsafe，以volatile语义在Segment数组中获取指定下标j的segement对象。
     * Gets the jth element of given segment array (if nonnull) with volatile element access semantics via Unsafe.
     * (The null check can trigger harmlessly only during deserialization.) Note: because each element of segments array is set only once
     * (using fully ordered writes), some performance-sensitive methods rely on this method only as a recheck upon null reads.
     */
    @SuppressWarnings("unchecked")
    static final <K, V> Segment<K, V> segmentAt(Segment<K, V>[] ss, int j) {
        long u = (j << SSHIFT) + SBASE;
        return ss == null ? null : (Segment<K, V>) UNSAFE.getObjectVolatile(ss, u);
    }

    /**
     * Returns the segment for the given index, creating it and recording in segment table (via CAS) if not already present.
     *
     * @param k the index
     * @return the segment
     */
    @SuppressWarnings("unchecked")
    private Segment<K, V> ensureSegment(int k) {
        final Segment<K, V>[] ss = this.segments;
        long u = (k << SSHIFT) + SBASE; // raw offset 计算k所在的segment的真实地址
        Segment<K, V> seg;

        if ((seg = (Segment<K, V>) UNSAFE.getObjectVolatile(ss, u)) == null) {
            //还是需要volatile读，只是这段代码的路径只会出现在初始化的这一个短暂的阶段中。

            //如果发现当前下标的segment对象为null，则以首节点为原型复制一个新的节点
            Segment<K, V> proto = ss[0]; // use segment 0 as prototype
            int cap = proto.table.length;
            float lf = proto.loadFactor;
            int threshold = (int) (cap * lf);
            HashEntry<K, V>[] tab = (HashEntry<K, V>[]) new HashEntry[cap];

            if ((seg = (Segment<K, V>) UNSAFE.getObjectVolatile(ss, u)) == null) { // recheck的意义在于其他线程可能会在这段时间内产生改动
                //以第一个节点为原型复制对象
                Segment<K, V> s = new Segment<K, V>(lf, threshold, tab);
                while ((seg = (Segment<K, V>) UNSAFE.getObjectVolatile(ss, u)) == null) {
                    if (UNSAFE.compareAndSwapObject(ss, u, null, seg = s))
                        break;
                }
            }
        }
        return seg;
    }

    // Hash-based segment and entry accesses

    /**
     * Get the segment for the given hash
     */
    @SuppressWarnings("unchecked")
    private Segment<K, V> segmentForHash(int h) {
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
        return (Segment<K, V>) UNSAFE.getObjectVolatile(segments, u);
    }

    /**
     * Gets the table entry for the given segment and hash
     */
    @SuppressWarnings("unchecked")
    static final <K, V> HashEntry<K, V> entryForHash(Segment<K, V> seg, int h) {
        HashEntry<K, V>[] tab;
        return (seg == null || (tab = seg.table) == null) ? null :
                (HashEntry<K, V>) UNSAFE.getObjectVolatile(tab, ((long) (((tab.length - 1) & h)) << TSHIFT) + TBASE);
    }

    /* ---------------- Public operations -------------- */

    /**
     * Creates a new, empty map with the specified initial capacity, load factor and concurrency level.
     *
     * @param initialCapacity  the initial capacity. The implementation performs internal sizing to accommodate this many elements.
     * @param loadFactor       the load factor threshold, used to control resizing. Resizing may be performed when the average number of elements per bin exceeds this threshold.
     * @param concurrencyLevel the estimated number of concurrently updating threads. The implementation performs internal sizing to try to accommodate this many threads.
     * @throws IllegalArgumentException if the initial capacity is negative or the load factor or concurrencyLevel are nonpositive.
     */
    @SuppressWarnings("unchecked") //屏蔽编译警告
    public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();

        if (concurrencyLevel > MAX_SEGMENTS)  //MAX_SEGMENTS = 2^16
            concurrencyLevel = MAX_SEGMENTS;

        // Find power-of-two sizes best matching arguments
        int sshift = 0;
        int ssize = 1;
        while (ssize < concurrencyLevel) { //因为MAX_SEGMENTS = 2^16，所以左移次数最多为16
            ++sshift;    //sshift最终为ssize的左移位数,如默认并发度为16，sshift即为4
            ssize <<= 1; //ssize最终为不小于concurrencyLevel的最大2的倍数
        }

        /***************************************************************************************************************
         * for example: concurrencyLevel = 33 / sshift = 6 / ssize = 64 / segmentShift = 26 / segmentMask = 5
         * 通过segmentShift和segmentMask就能知道segment的位置了
         ***************************************************************************************************************/
        this.segmentShift = 32 - sshift;
        this.segmentMask = ssize - 1;

        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;

        int c = initialCapacity / ssize;
        if (c * ssize < initialCapacity)
            ++c;

        int cap = MIN_SEGMENT_TABLE_CAPACITY;
        while (cap < c)
            cap <<= 1;

        /**
         * create segments and segments[0]
         * 除第一个Segment外，剩余Segments采用迟初始化机制：每次put之前都需要检查key对应的Segment是否为null，如果是则调用ensureSegment()保对应的Segment被创建。
         * 延迟初始化机制的意义是什么呢?
         */

        Segment<K, V>[] ss = (Segment<K, V>[]) new Segment[ssize]; //初始化数组

        //初始化一个segment对象，并安置在首节点。
        Segment<K, V> s0 = new Segment<K, V>(loadFactor, (int) (cap * loadFactor), (HashEntry<K, V>[]) new HashEntry[cap]);
        UNSAFE.putOrderedObject(ss, SBASE, s0); // ordered write of segments[0]

        this.segments = ss;
    }

    /**
     * Creates a new, empty map with the specified initial capacity and load factor and with the default concurrencyLevel(16).
     *
     * @param initialCapacity The implementation performs internal sizing to accommodate this many elements.
     * @param loadFactor      the load factor threshold, used to control resizing. Resizing may be performed when the average number of elements per bin exceeds this threshold.
     * @throws IllegalArgumentException if the initial capacity of elements is negative or the load factor is nonpositive
     * @since 1.6
     */
    public ConcurrentHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new, empty map with the specified initial capacity, and with default load factor (0.75) and concurrencyLevel (16).
     *
     * @param initialCapacity the initial capacity. The implementation performs internal sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of elements is negative.
     */
    public ConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new, empty map with a default initial capacity(16), load factor(0.75) and concurrencyLevel(16).
     */
    public ConcurrentHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new map with the same mappings as the given map.
     * The map is created with a capacity of 1.5 times the number of mappings in the given map or 16 (whichever is greater),
     * and a default load factor 0.75 and concurrencyLevel 16.
     *
     * @param m the map
     */
    public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1, DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
        putAll(m);
    }

    /**
     * Returns true if this map contains no key-value mappings.
     *
     * @return true if this map contains no key-value mappings
     */
    public boolean isEmpty() {
        /*
         * Sum per-segment modCounts to avoid mis-reporting when elements are concurrently added and removed in one segment while checking another,
         * in which case the table was never actually empty at any point. (The sum ensures accuracy up through at least 1<<31 per-segment modifications
         * before recheck) Methods size() and containsValue() use similar constructions for stability checks.
         */
        long sum = 0L;
        final Segment<K, V>[] segments = this.segments;
        for (int j = 0; j < segments.length; ++j) {
            Segment<K, V> seg = segmentAt(segments, j); //获取指定下标的segment对象
            if (seg != null) {
                if (seg.count != 0)
                    return false; //只要有一个segment对象的对象不为空，就应该返回false。
                sum += seg.modCount;
            }
        }

        if (sum != 0L) { // recheck unless no modifications
            for (int j = 0; j < segments.length; ++j) {
                Segment<K, V> seg = segmentAt(segments, j);
                if (seg != null) {
                    if (seg.count != 0)
                        return false;
                    sum -= seg.modCount;
                }
            }
            if (sum != 0L) //说明在这期间又发生了改变，必然也就不为空了。
                return false;
        }
        return true;
    }

    /**
     * Returns the number of key-value mappings in this map. If the map contains more than Integer.MAX_VALUE elements, returns Integer.MAX_VALUE.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        // Try a few times to get accurate count. On failure due to continuous async changes in table, resort to locking.
        final Segment<K, V>[] segments = this.segments;
        int size;
        boolean overflow; // true if size overflows 32 bits. 标识是否超过Integer.MAX_VALUE
        long sum;         // sum of modCounts
        long last = 0L;   // previous sum
        int retries = -1; // first iteration isn't retry

        try {
            for (; ; ) {
                if (retries++ == RETRIES_BEFORE_LOCK) {
                    for (int j = 0; j < segments.length; ++j)
                        //依次去拿锁
                        ensureSegment(j).lock(); // force creation
                }
                sum = 0L;
                size = 0;
                overflow = false;
                for (int j = 0; j < segments.length; ++j) {
                    Segment<K, V> seg = segmentAt(segments, j);
                    if (seg != null) {
                        sum += seg.modCount;
                        int c = seg.count;
                        if (c < 0 || (size += c) < 0)
                            overflow = true;
                    }
                }
                if (sum == last)
                    break;
                last = sum;
            }
        } finally {
            //依次去放锁
            if (retries > RETRIES_BEFORE_LOCK) {
                for (int j = 0; j < segments.length; ++j)
                    segmentAt(segments, j).unlock();
            }
        }
        return overflow ? Integer.MAX_VALUE : size;
    }

    /**
     * Returns the value to which the specified key is mapped, or null if this map contains no mapping for the key.
     * 
     * More formally, if this map contains a mapping from a key k to a value v such that key.equals(k), then this method returns v;
     * otherwise it returns null. (There can be at most one such mapping)
     *
     * @throws NullPointerException if the specified key is null
     */
    public V get(Object key) {
        Segment<K, V> s; // manually integrate access methods to reduce overhead
        HashEntry<K, V>[] tab;

        int h = hash(key); //先计算出key的hash值
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE; //计算segment的下标
        if ((s = (Segment<K, V>) UNSAFE.getObjectVolatile(segments, u)) != null && (tab = s.table) != null) {
            for (HashEntry<K, V> e = (HashEntry<K, V>) UNSAFE.getObjectVolatile(tab, ((long) (((tab.length - 1) & h)) << TSHIFT) + TBASE);
                 e != null;
                 e = e.next) {
                //这里遍历链表的过程中，不需要抢锁是因为next本身就是volatile的
                K k;
                if ((k = e.key) == key || (e.hash == h && key.equals(k)))
                    return e.value;
            }
        }
        return null;
    }

    /**
     * Tests if the specified object is a key in this table.
     *
     * @param key possible key
     * @return true if and only if the specified object is a key in this table, as determined by the equals method; false otherwise.
     * @throws NullPointerException if the specified key is null
     */
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
        Segment<K, V> s; // same as get() except no need for volatile value read
        HashEntry<K, V>[] tab;
        int h = hash(key);
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;

        if ((s = (Segment<K, V>) UNSAFE.getObjectVolatile(segments, u)) != null && (tab = s.table) != null) {
            for (HashEntry<K, V> e = (HashEntry<K, V>) UNSAFE.getObjectVolatile(tab, ((long) (((tab.length - 1) & h)) << TSHIFT) + TBASE);
                 e != null;
                 e = e.next) {

                K k;
                if ((k = e.key) == key || (e.hash == h && key.equals(k)))
                    return true;
            }
        }
        return false;
    }

    /**
     * Returns true if this map maps one or more keys to the specified value. Note: This method requires a full internal
     * traversal of the hash table, and so is much slower than method containsKey.
     *
     * @param value value whose presence in this map is to be tested
     * @return true if this map maps one or more keys to the specified value
     * @throws NullPointerException if the specified value is null
     */
    public boolean containsValue(Object value) {
        // Same idea as size()
        if (value == null)
            throw new NullPointerException();
        final Segment<K, V>[] segments = this.segments;
        boolean found = false;
        long last = 0;
        int retries = -1;
        try {
            outer:
            for (; ; ) {
                if (retries++ == RETRIES_BEFORE_LOCK) {
                    for (int j = 0; j < segments.length; ++j)
                        ensureSegment(j).lock(); // force creation
                }
                long hashSum = 0L;
                int sum = 0;
                for (int j = 0; j < segments.length; ++j) {
                    HashEntry<K, V>[] tab;
                    Segment<K, V> seg = segmentAt(segments, j);
                    if (seg != null && (tab = seg.table) != null) {
                        for (int i = 0; i < tab.length; i++) {
                            HashEntry<K, V> e;
                            for (e = entryAt(tab, i); e != null; e = e.next) {
                                V v = e.value;
                                if (v != null && value.equals(v)) {
                                    found = true;
                                    break outer;
                                }
                            }
                        }
                        sum += seg.modCount;
                    }
                }
                if (retries > 0 && sum == last)
                    break;
                last = sum;
            }
        } finally {
            if (retries > RETRIES_BEFORE_LOCK) {
                for (int j = 0; j < segments.length; ++j)
                    segmentAt(segments, j).unlock();
            }
        }
        return found;
    }

    /**
     * Legacy method testing if some key maps into the specified value in this table.
     * This method is identical in functionality to containsValue, and exists solely to ensure full compatibility with class java.util.Hashtable,
     * which supported this method prior to introduction of the Java Collections framework.
     *
     * @param value a value to search for
     * @return true if and only if some key maps to the value argument in this table as determined by the equals method; false otherwise
     * @throws NullPointerException if the specified value is null
     */
    public boolean contains(Object value) {
        return containsValue(value);
    }

    /**
     * Maps the specified key to the specified value in this table.
     *
     * Neither the key nor the value can be null.(ConcurrentHashMap插入的key和value都不能为null)
     * 
     * The value can be retrieved by calling the get method with a key that is equal to the original key.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with key, or null if there was no mapping for key
     * @throws NullPointerException if the specified key or value is null
     */
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        Segment<K, V> s;

        if (value == null) //ConcurrentHashMap不允许value为null
            throw new NullPointerException();

        int hash = hash(key);
        int j = (hash >>> segmentShift) & segmentMask;   // 计算segment的下标

        /**
         * 这是一个优化性能的细节：
         * 首先要明确的是Segment数组采用的是延迟初始化，在除了第一个位置的Segment外都不会进行初始化。所以在进行get/put操作时，可能会出现数组某个下标的segment出现并发问题。
         * 即另一个线程已经初始化这个segment对象了，但本线程还读到null。这种情况完全可以通过volatile + CAS读写来避免。但是这种操作是有代价的，而且这种不一致的情况仅仅在数组
         * 节点首次初始化时才会发生，没有必要为了这个特殊情况而浪费其他场景的性能。
         *
         * 所以这里没有用UNSAFE.getObjectVolatile()，而是采用了UNSAFE.getObject()。如果读取的segment不为null，这就是正常的，为null就再去确认一下。
         */
        if ((s = (Segment<K, V>) UNSAFE.getObject(segments, (j << SSHIFT) + SBASE)) == null)
            //  in ensureSegment
            s = ensureSegment(j);

        return s.put(key, hash, value, false);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     * or null if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    @SuppressWarnings("unchecked")
    public V putIfAbsent(K key, V value) {
        Segment<K, V> s;
        if (value == null)
            throw new NullPointerException();

        int hash = hash(key);
        int j = (hash >>> segmentShift) & segmentMask;
        if ((s = (Segment<K, V>) UNSAFE.getObject(segments, (j << SSHIFT) + SBASE)) == null)
            s = ensureSegment(j);

        return s.put(key, hash, value, true);
    }

    /**
     * Copies all of the mappings from the specified map to this one.
     * These mappings replace any mappings that this map had for any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     * Removes the key (and its corresponding value) from this map.
     * This method does nothing if the key is not in the map.
     *
     * @param key the key that needs to be removed
     * @return the previous value associated with key, or
     * null if there was no mapping for key
     * @throws NullPointerException if the specified key is null
     */
    public V remove(Object key) {
        int hash = hash(key);
        Segment<K, V> s = segmentForHash(hash);
        return s == null ? null : s.remove(key, hash, null);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified key is null
     */
    public boolean remove(Object key, Object value) {
        int hash = hash(key);
        Segment<K, V> s;
        return value != null && (s = segmentForHash(hash)) != null && s.remove(key, hash, value) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if any of the arguments are null
     */
    public boolean replace(K key, V oldValue, V newValue) {
        int hash = hash(key);
        if (oldValue == null || newValue == null)
            throw new NullPointerException();

        Segment<K, V> s = segmentForHash(hash);
        return s != null && s.replace(key, hash, oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key, or null if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    public V replace(K key, V value) {
        int hash = hash(key);
        if (value == null)
            throw new NullPointerException();
        Segment<K, V> s = segmentForHash(hash);
        return s == null ? null : s.replace(key, hash, value);
    }

    /**
     * Removes all of the mappings from this map.
     */
    public void clear() {
        final Segment<K, V>[] segments = this.segments;
        for (int j = 0; j < segments.length; ++j) {
            Segment<K, V> s = segmentAt(segments, j);
            if (s != null)
                s.clear();
        }
    }

    /**
     * Returns a  Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are reflected in the set, and vice-versa. The set supports element removal, which removes
     * the corresponding mapping from this map, via the Iterator.remove, Set.remove, removeAll, retainAll, and clear operations.
     *
     * It does not support the add or addAll operations.
     * 
     * The view's iterator is a "weakly consistent" iterator that will never throw  ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null) ? ks : (keySet = new KeySet());
    }

    /**
     * Returns a  Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are reflected in the collection, and vice-versa.
     * The collection supports element removal, which removes the corresponding mapping from this map, via the Iterator.remove,
     * Collection.remove, removeAll, retainAll, and clear operations.
     * It does not support the add or addAll operations.
     * 
     * The view's iterator is a "weakly consistent" iterator that will never throw  ConcurrentModificationException,
     * and guarantees to traverse elements as they existed upon construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null) ? vs : (values = new Values());
    }

    /**
     * Returns a  Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from the map, via the Iterator.remove, Set.remove, removeAll, retainAll, and clear
     * operations.
     * It does not support the add or addAll operations.
     * 
     * The view's iterator is a "weakly consistent" iterator that will never throw ConcurrentModificationException,
     * and guarantees to traverse elements as they existed upon construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return an enumeration of the keys in this table
     * @see #keySet()
     */
    public Enumeration<K> keys() {
        return new KeyIterator();
    }

    /**
     * Returns an enumeration of the values in this table.
     *
     * @return an enumeration of the values in this table
     * @see #values()
     */
    public Enumeration<V> elements() {
        return new ValueIterator();
    }

    /* ---------------- Iterator Support -------------- */

    abstract class HashIterator {
        int nextSegmentIndex;
        int nextTableIndex;
        HashEntry<K, V>[] currentTable;
        HashEntry<K, V> nextEntry;
        HashEntry<K, V> lastReturned;

        HashIterator() {
            nextSegmentIndex = segments.length - 1;
            nextTableIndex = -1;
            advance();
        }

        /**
         * Set nextEntry to first node of next non-empty table
         * (in backwards order, to simplify checks).
         */
        final void advance() {
            for (; ; ) {
                if (nextTableIndex >= 0) {
                    if ((nextEntry = entryAt(currentTable,
                            nextTableIndex--)) != null)
                        break;
                } else if (nextSegmentIndex >= 0) {
                    Segment<K, V> seg = segmentAt(segments, nextSegmentIndex--);
                    if (seg != null && (currentTable = seg.table) != null)
                        nextTableIndex = currentTable.length - 1;
                } else
                    break;
            }
        }

        final HashEntry<K, V> nextEntry() {
            HashEntry<K, V> e = nextEntry;
            if (e == null)
                throw new NoSuchElementException();
            lastReturned = e; // cannot assign until after null check
            if ((nextEntry = e.next) == null)
                advance();
            return e;
        }

        public final boolean hasNext() {
            return nextEntry != null;
        }

        public final boolean hasMoreElements() {
            return nextEntry != null;
        }

        public final void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            ConcurrentHashMap.this.remove(lastReturned.key);
            lastReturned = null;
        }
    }

    final class KeyIterator
            extends HashIterator
            implements Iterator<K>, Enumeration<K> {
        public final K next() {
            return super.nextEntry().key;
        }

        public final K nextElement() {
            return super.nextEntry().key;
        }
    }

    final class ValueIterator
            extends HashIterator
            implements Iterator<V>, Enumeration<V> {
        public final V next() {
            return super.nextEntry().value;
        }

        public final V nextElement() {
            return super.nextEntry().value;
        }
    }

    /**
     * Custom Entry class used by EntryIterator.next(), that relays
     * setValue changes to the underlying map.
     */
    final class WriteThroughEntry
            extends AbstractMap.SimpleEntry<K, V> {
        WriteThroughEntry(K k, V v) {
            super(k, v);
        }

        /**
         * Set our entry's value and write through to the map. The
         * value to return is somewhat arbitrary here. Since a
         * WriteThroughEntry does not necessarily track asynchronous
         * changes, the most recent "previous" value could be
         * different from what we return (or could even have been
         * removed in which case the put will re-establish). We do not
         * and cannot guarantee more.
         */
        public V setValue(V value) {
            if (value == null) throw new NullPointerException();
            V v = super.setValue(value);
            ConcurrentHashMap.this.put(getKey(), value);
            return v;
        }
    }

    final class EntryIterator
            extends HashIterator
            implements Iterator<Entry<K, V>> {
        public Map.Entry<K, V> next() {
            HashEntry<K, V> e = super.nextEntry();
            return new WriteThroughEntry(e.key, e.value);
        }
    }

    final class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return new KeyIterator();
        }

        public int size() {
            return ConcurrentHashMap.this.size();
        }

        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }

        public boolean contains(Object o) {
            return ConcurrentHashMap.this.containsKey(o);
        }

        public boolean remove(Object o) {
            return ConcurrentHashMap.this.remove(o) != null;
        }

        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    final class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }

        public int size() {
            return ConcurrentHashMap.this.size();
        }

        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }

        public boolean contains(Object o) {
            return ConcurrentHashMap.this.containsValue(o);
        }

        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        public Iterator<Map.Entry<K, V>> iterator() {
            return new EntryIterator();
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            V v = ConcurrentHashMap.this.get(e.getKey());
            return v != null && v.equals(e.getValue());
        }

        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return ConcurrentHashMap.this.remove(e.getKey(), e.getValue());
        }

        public int size() {
            return ConcurrentHashMap.this.size();
        }

        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }

        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    /* ---------------- Serialization Support -------------- */

    /**
     * Save the state of the ConcurrentHashMap instance to a
     * stream (i.e., serialize it).
     *
     * @param s the stream
     * @serialData the key (Object) and value (Object)
     * for each key-value mapping, followed by a null pair.
     * The key-value mappings are emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s) throws IOException {
        // force all segments for serialization compatibility
        for (int k = 0; k < segments.length; ++k)
            ensureSegment(k);
        s.defaultWriteObject();

        final Segment<K, V>[] segments = this.segments;
        for (int k = 0; k < segments.length; ++k) {
            Segment<K, V> seg = segmentAt(segments, k);
            seg.lock();
            try {
                HashEntry<K, V>[] tab = seg.table;
                for (int i = 0; i < tab.length; ++i) {
                    HashEntry<K, V> e;
                    for (e = entryAt(tab, i); e != null; e = e.next) {
                        s.writeObject(e.key);
                        s.writeObject(e.value);
                    }
                }
            } finally {
                seg.unlock();
            }
        }
        s.writeObject(null);
        s.writeObject(null);
    }

    /**
     * Reconstitute the ConcurrentHashMap instance from a
     * stream (i.e., deserialize it).
     *
     * @param s the stream
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        // Don't call defaultReadObject()
        ObjectInputStream.GetField oisFields = s.readFields();
        final Segment<K, V>[] oisSegments = (Segment<K, V>[]) oisFields.get("segments", null);

        final int ssize = oisSegments.length;
        if (ssize < 1 || ssize > MAX_SEGMENTS
                || (ssize & (ssize - 1)) != 0)  // ssize not power of two
            throw new java.io.InvalidObjectException("Bad number of segments:"
                    + ssize);
        int sshift = 0, ssizeTmp = ssize;
        while (ssizeTmp > 1) {
            ++sshift;
            ssizeTmp >>>= 1;
        }
        UNSAFE.putIntVolatile(this, SEGSHIFT_OFFSET, 32 - sshift);
        UNSAFE.putIntVolatile(this, SEGMASK_OFFSET, ssize - 1);
        UNSAFE.putObjectVolatile(this, SEGMENTS_OFFSET, oisSegments);

        // set hashMask
        UNSAFE.putIntVolatile(this, HASHSEED_OFFSET, randomHashSeed(this));

        // Re-initialize segments to be minimally sized, and let grow.
        int cap = MIN_SEGMENT_TABLE_CAPACITY;
        final Segment<K, V>[] segments = this.segments;
        for (int k = 0; k < segments.length; ++k) {
            Segment<K, V> seg = segments[k];
            if (seg != null) {
                seg.threshold = (int) (cap * seg.loadFactor);
                seg.table = (HashEntry<K, V>[]) new HashEntry[cap];
            }
        }

        // Read the keys and values, and put the mappings in the table
        for (; ; ) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            if (key == null)
                break;
            put(key, value);
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long SBASE;
    private static final int SSHIFT;
    private static final long TBASE;
    private static final int TSHIFT;
    private static final long HASHSEED_OFFSET;
    private static final long SEGSHIFT_OFFSET;
    private static final long SEGMASK_OFFSET;
    private static final long SEGMENTS_OFFSET;

    static {
        int ss, ts;
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class tc = HashEntry[].class;
            Class sc = Segment[].class;
            TBASE = UNSAFE.arrayBaseOffset(tc);
            SBASE = UNSAFE.arrayBaseOffset(sc); //segment数组的起始位置
            ts = UNSAFE.arrayIndexScale(tc);
            ss = UNSAFE.arrayIndexScale(sc);
            HASHSEED_OFFSET = UNSAFE.objectFieldOffset(
                    ConcurrentHashMap.class.getDeclaredField("hashSeed"));
            SEGSHIFT_OFFSET = UNSAFE.objectFieldOffset(
                    ConcurrentHashMap.class.getDeclaredField("segmentShift"));
            SEGMASK_OFFSET = UNSAFE.objectFieldOffset(
                    ConcurrentHashMap.class.getDeclaredField("segmentMask"));
            SEGMENTS_OFFSET = UNSAFE.objectFieldOffset(
                    ConcurrentHashMap.class.getDeclaredField("segments"));
        } catch (Exception e) {
            throw new Error(e);
        }
        if ((ss & (ss - 1)) != 0 || (ts & (ts - 1)) != 0)
            throw new Error("data type scale not a power of two");
        SSHIFT = 31 - Integer.numberOfLeadingZeros(ss);
        TSHIFT = 31 - Integer.numberOfLeadingZeros(ts);
    }

}
