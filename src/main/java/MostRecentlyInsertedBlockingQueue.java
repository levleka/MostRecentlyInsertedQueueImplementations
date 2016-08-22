import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MostRecentlyInsertedBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

    private final E[] elements;

    private int size;

    private int nextHeadIndex;

    private int nextTailIndex;


    private final ReentrantLock mainLock;

    private final Condition notEmpty;

    private final Condition notFull;


    private int increment(int index) {
        return (++index == elements.length) ? 0 : index;
    }

    private void insert(E e) {
        elements[nextTailIndex] = e;
        nextTailIndex = increment(nextTailIndex);
        ++size;
        notEmpty.signal();
    }

    private E extract() {
        E[] elements = this.elements;
        E e = elements[nextHeadIndex];
        elements[nextHeadIndex] = null;
        nextHeadIndex = increment(nextHeadIndex);
        --size;
        notFull.signal();
        return e;
    }

    public MostRecentlyInsertedBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        elements = (E[]) new Object[capacity];
        mainLock = new ReentrantLock();
        notFull = mainLock.newCondition();
        notEmpty = mainLock.newCondition();
    }

    @Override
    public Iterator<E> iterator() {
        final ReentrantLock lock = mainLock;
        lock.lock();
        try {
            return new Iterator<E>() {
                private int index = nextHeadIndex;
                private int lastReturnedIndex = -1;
                private boolean isFirst = size == elements.length;


                @Override
                public boolean hasNext() {
                    return isFirst || index != nextTailIndex;
                }

                @Override
                public E next() {
                    final ReentrantLock lock = MostRecentlyInsertedBlockingQueue.this.mainLock;
                    lock.lock();
                    try {
                        if (!hasNext()) throw new NoSuchElementException();
                        isFirst = false;
                        lastReturnedIndex = index;
                        index = increment(index);
                        return elements[lastReturnedIndex];
                    } finally {
                        lock.unlock();
                    }
                }
            };
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        final ReentrantLock lock = mainLock;
        lock.lock();
        try {
            return size;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        final E[] elements = this.elements;
        final ReentrantLock lock = mainLock;
        lock.lockInterruptibly();
        try {
            try {
                while (size == elements.length) {
                    notFull.await();
                }
            } catch (InterruptedException ie) {
                notFull.signal();
                throw ie;
            }
            insert(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = mainLock;
        lock.lockInterruptibly();
        try {
            while (true) {
                if (size != elements.length) {
                    insert(e);
                    return true;
                }

                if (nanos <= 0) {
                    return false;
                }
                try {
                    nanos = notFull.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    notFull.signal();
                    throw ie;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E take() throws InterruptedException {
        final ReentrantLock lock = mainLock;
        lock.lockInterruptibly();
        try {
            try {
                while (size == 0) {
                    notEmpty.await();
                }
            } catch (InterruptedException ie) {
                notEmpty.signal();
                throw ie;
            }
            E e = extract();
            return e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = mainLock;
        lock.lockInterruptibly();
        try {
            while (true) {
                if (size != 0) {
                    E e = extract();
                    return e;
                }
                if (nanos <= 0) return null;
                try {
                    nanos = notEmpty.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    notEmpty.signal();
                    throw ie;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int remainingCapacity() {
        final ReentrantLock lock = mainLock;
        lock.lock();
        try {
            return elements.length - size;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        final ReentrantLock lock = mainLock;
        lock.lock();
        try {
            if (size == elements.length) {
                remove();
                insert(e);
                return true;
            } else {
                insert(e);
                return true;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E poll() {
        final ReentrantLock lock = mainLock;
        lock.lock();
        try {
            if (size == 0) return null;
            E e = extract();
            return e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E peek() {
        final ReentrantLock lock = this.mainLock;
        lock.lock();
        try {
            return (size == 0) ? null : elements[nextHeadIndex];
        } finally {
            lock.unlock();
        }
    }
}