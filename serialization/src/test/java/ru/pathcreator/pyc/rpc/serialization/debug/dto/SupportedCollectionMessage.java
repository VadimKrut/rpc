package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.annotation.RpcFixedLength;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

import java.time.LocalDate;
import java.util.*;

@SbeSerializable
public record SupportedCollectionMessage(
        long batchId,
        Collection<Entry> collectionEntries,
        List<Entry> listEntries,
        Set<Entry> setEntries,
        Queue<Entry> queueEntries,
        Deque<Entry> dequeEntries,
        ArrayList<Entry> arrayListEntries,
        LinkedHashSet<Entry> linkedHashSetEntries,
        ArrayDeque<Entry> arrayDequeEntries,
        Entry[] arrayEntries
) {
    @SbeSerializable
    public record Entry(
            long id,
            int quantity,
            LocalDate tradeDate,
            SupportedSide side,
            @RpcFixedLength(12) String venue
    ) {
    }
}