package io.github.vfedoriv.graphrag.ai.models;

import java.util.Iterator;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;

public final class EmptyObjectProvider<T> implements ObjectProvider<T> {

    @Override
    public T getObject(Object... args) {
        return null;
    }

    @Override
    public T getIfAvailable() {
        return null;
    }

    @Override
    public Iterator<T> iterator() {
        return Stream.<T>empty().iterator();
    }

    @Override
    public Stream<T> stream() {
        return Stream.empty();
    }

    @Override
    public Stream<T> orderedStream() {
        return Stream.empty();
    }
}
