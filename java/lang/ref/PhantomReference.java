/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */

package java.lang.ref;


/**
 * Phantom reference objects, which are enqueued after the collector determines that their referents may otherwise be reclaimed.
 *
 * Phantom references are most often used for scheduling pre-mortem cleanup actions in a more flexible way than is possible with the Java finalization mechanism.
 *
 * If the garbage collector determines at a certain point in time that the referent of a phantom reference is phantom reachable, then at that
 * time or at some later time it will enqueue the reference.
 *
 * In order to ensure that a reclaimable object remains so, the referent of a phantom reference may not be retrieved: The get method of a
 * phantom reference always returns null.
 *
 * Unlike soft and weak references, phantom references are not automatically cleared by the garbage collector as they are enqueued.  
 * An object that is reachable via phantom references will remain so until all such references are cleared or themselves become unreachable.
 *
 * @author   Mark Reinhold
 * @since    1.2
 */

public class PhantomReference<T> extends Reference<T> {

    /**
     * Returns this reference object's referent.
     * Because the referent of a phantom reference is always inaccessible, this method always returns null.
     *
     * @return null
     */
    public T get() {
        return null;
    }

    /**
     * Creates a new phantom reference that refers to the given object and is registered with the given queue.
     *
     * It is possible to create a phantom reference with a null queue, but such a reference is completely useless: Its get
     * method will always return null and, since it does not have a queue, it will never be enqueued.
     *
     * @param referent the object the new phantom reference will refer to
     * @param q the queue with which the reference is to be registered, or null if registration is not required
     */
    public PhantomReference(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
    }

}
