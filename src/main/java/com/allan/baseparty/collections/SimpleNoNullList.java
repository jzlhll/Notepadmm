package com.allan.baseparty.collections;

import com.allan.baseparty.exception.UnImplementException;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * 本List适用于极少的变动数组。
 * 每次add: 我们仅仅是直接操作数组+1。
 * 每次remove：也会直接减低分配。
 * @param <E>
 */
public final class SimpleNoNullList<E> implements Collection<E> {
    private class It implements Iterator<E> {
        private int itSize = 0;

        @Override
        public boolean hasNext() {
            return itSize != size();
        }

        @Override
        public E next() {
            return (E) list[itSize++];
        }
    }

    private Object[] list;

    @Override
    public int size() {
        return list == null ? 0 : list.length;
    }

    @Override
    public boolean isEmpty() {
        return list == null || list.length == 0;
    }

    @Override
    public boolean contains(Object o) {
        if (list != null) {
            for (Object obj : list) {
                if (Objects.equals(o, obj)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean add(@NotNull E e) {
        if (list == null) {
            Object[] newList = new Object[1];
            newList[0] = e;
            list = newList;
        } else {
            Object[] newList = new Object[list.length + 1];
            int i = 0;
            for (Object o : list) {
                newList[i++] = o;
            }
            newList[i] = e;

            list = newList;
        }
        return true;
    }

    @Override
    public boolean remove(@NotNull Object o) {
        Object[] curList = list;

        int len;
        int index = -1;
        if (curList != null) {
            len = curList.length;
            for (int i = 0; i < len; i++) {
                if (Objects.equals(o, curList[i])) {
                    //todo: 我们一次只删除一个。不做全部删除
                    curList[i] = null;
                    index = i;
                    break;
                }
            }
        } else {
            len = 0;
        }

        if (index >= 0) {
            //重置数据
            Object[] newList = new Object[len - 1];
            int newIndex = 0;

            for (int i = 0; i < len; i++) {
                if (curList[i] != null) {
                    newList[newIndex++] = curList[i];
                    curList[i] = null;
                }
            }

            list = newList;
        }

        return false;
    }

    @Override
    public void clear() {
        Object[] oldList = list;
        list = null;
        if (oldList != null) {
            for (int to = oldList.length, i = 0; i < to; i++)
                oldList[i] = null;
        }
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
        return new It();
    }

    //===========
    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        throw new UnImplementException();
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
        throw new UnImplementException();
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        throw new UnImplementException();
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        throw new UnImplementException();
    }
    @NotNull
    @Override
    public Object[] toArray() {
        throw new UnImplementException();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        throw new UnImplementException();
    }
}
