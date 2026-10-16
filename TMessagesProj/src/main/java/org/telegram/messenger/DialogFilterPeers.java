package org.telegram.messenger;

import java.util.Map;
import java.util.function.LongPredicate;
import org.telegram.tgnet.TLRPC;

/** Peers required to render a folder, independently of its pinned state. */
public final class DialogFilterPeers {
    private DialogFilterPeers() {}

    public static void collectIncluded(TLRPC.DialogFilter filter, Map<Long, TLRPC.InputPeer> peers) {
        for (TLRPC.InputPeer peer : filter.pinned_peers) add(peer, peers);
        for (TLRPC.InputPeer peer : filter.include_peers) add(peer, peers);
    }

    private static void add(TLRPC.InputPeer peer, Map<Long, TLRPC.InputPeer> peers) {
        if (peer == null) return;
        long id = peer.user_id != 0 ? peer.user_id : -(peer.chat_id != 0 ? peer.chat_id : peer.channel_id);
        if (id != 0) peers.put(id, peer);
    }

    public static boolean hasMissing(Iterable<Long> includedIds, LongPredicate isLoaded) {
        for (long id : includedIds) {
            if (id != 0 && !isLoaded.test(id)) return true;
        }
        return false;
    }
}
