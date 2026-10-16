import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import org.telegram.messenger.DialogFilterPeers;
import org.telegram.tgnet.TLRPC;

/** Runs against the production folder peer collector, without an account. */
public class FolderPeersCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        TLRPC.TL_dialogFilter folder = new TLRPC.TL_dialogFilter();
        TLRPC.TL_inputPeerChannel pinned = new TLRPC.TL_inputPeerChannel();
        pinned.channel_id = 101; pinned.access_hash = 123;
        TLRPC.TL_inputPeerChannel included = new TLRPC.TL_inputPeerChannel();
        included.channel_id = 202; included.access_hash = 456;
        TLRPC.TL_inputPeerUser user = new TLRPC.TL_inputPeerUser();
        user.user_id = 303; user.access_hash = 789;
        TLRPC.TL_inputPeerChat group = new TLRPC.TL_inputPeerChat(); group.chat_id = 404;
        TLRPC.TL_inputPeerChannel excluded = new TLRPC.TL_inputPeerChannel(); excluded.channel_id = 505;
        folder.pinned_peers.add(pinned);
        folder.include_peers.addAll(Arrays.asList(pinned, included, user, group));
        folder.exclude_peers.add(excluded);
        HashMap<Long, TLRPC.InputPeer> pending = new HashMap<>();
        DialogFilterPeers.collectIncluded(folder, pending);
        check(pending.size() == 4 && pending.get(-101L) == pinned && pending.get(-202L) == included
                && pending.get(303L) == user && pending.get(-404L) == group, "Load unpinned users, groups and channels as dialogs");
        check(pending.get(-202L).access_hash == 456, "Keep server access hash for getPeerDialogs");
        check(!pending.containsKey(-505L), "Do not include excluded peers");
        DialogFilterPeers.collectIncluded(folder, pending);
        check(pending.size() == 4, "Unchanged folders and overlapping folders deduplicate");
        folder.pinned_peers.clear();
        pending.clear(); DialogFilterPeers.collectIncluded(folder, pending);
        check(pending.containsKey(-202L) && pending.size() == 4, "Unpinned-only folders still load their dialogs");

        HashSet<Long> cached = new HashSet<>(Arrays.asList(-101L, 303L, -404L));
        check(DialogFilterPeers.hasMissing(pending.keySet(), cached::contains), "Missing archived channel repairs a previously loaded folder");
        cached.add(-202L);
        check(!DialogFilterPeers.hasMissing(pending.keySet(), cached::contains), "Complete cache does not force a remote reload");
        check(!DialogFilterPeers.hasMissing(Arrays.asList(0L), cached::contains), "Ignore empty peer id");
        TLRPC.TL_dialogFilter empty = new TLRPC.TL_dialogFilter(); empty.include_peers.add(new TLRPC.TL_inputPeerEmpty());
        pending.clear(); DialogFilterPeers.collectIncluded(empty, pending);
        check(pending.isEmpty(), "Empty peer must not become dialog zero");
        System.out.println("Folder peer loading regression checks passed.");
    }
}
