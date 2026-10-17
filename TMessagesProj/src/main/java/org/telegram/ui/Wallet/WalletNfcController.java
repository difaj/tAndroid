package org.telegram.ui.Wallet;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.tech.IsoDep;
import android.os.SystemClock;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Foreground-only HCE receiver and one-shot reader. All UI callbacks run on main. */
public final class WalletNfcController {
    public interface ReaderCallback {
        void onAddress(String address);
        void onError();
    }
    public enum Availability { READY, UNSUPPORTED, DISABLED }
    private static final class Receiver {
        final WalletNfcController owner;
        final String address;
        final WalletNfcProtocol.Session session;
        final Runnable shared;
        Receiver(WalletNfcController owner, String address, Runnable shared) {
            this.owner = owner; this.address = address; this.shared = shared;
            session = new WalletNfcProtocol.Session(address);
        }
    }
    private static volatile Receiver receiver;
    private final Activity activity;
    private final NfcAdapter adapter;
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicBoolean reading = new AtomicBoolean(), busy = new AtomicBoolean();
    private volatile IsoDep connection;
    private long lastErrorAt;
    private boolean readerModeEnabled;

    public WalletNfcController(Activity activity) {
        this.activity = activity;
        NfcAdapter found = null;
        try { found = NfcAdapter.getDefaultAdapter(activity); } catch (RuntimeException e) { FileLog.e(e); }
        adapter = found;
    }

    public Availability availability() {
        if (adapter == null) return Availability.UNSUPPORTED;
        try { return adapter.isEnabled() ? Availability.READY : Availability.DISABLED; }
        catch (RuntimeException e) { return Availability.UNSUPPORTED; }
    }

    public boolean isForActivity(Activity current) { return activity == current; }

    public void receive(String address, Runnable shared) {
        if (reading.get() || availability() != Availability.READY
                || !activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            stopReceiving();
            return;
        }
        Receiver old = receiver;
        if (old != null && old.owner == this && old.address.equals(address)) return;
        stopReceiving();
        if (old != null) old.owner.stopReceiving();
        try {
            if (!CardEmulation.getInstance(adapter).setPreferredService(activity, new ComponentName(activity, WalletNfcService.class))) return;
            receiver = new Receiver(this, address, shared);
        } catch (RuntimeException e) { FileLog.e(e); }
    }

    private void stopReceiving() {
        Receiver old = receiver;
        if (old == null || old.owner != this) return;
        receiver = null; old.session.close();
        try { CardEmulation.getInstance(adapter).unsetPreferredService(activity); }
        catch (RuntimeException e) { FileLog.e(e); }
    }

    static byte[] processCommand(byte[] command) {
        final Receiver current = receiver;
        if (current == null || ApplicationLoader.mainInterfacePaused || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) {
            return WalletNfcProtocol.UNAVAILABLE.clone();
        }
        WalletNfcProtocol.Result result = current.session.process(command, SystemClock.elapsedRealtime());
        if (receiver != current || ApplicationLoader.mainInterfacePaused || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) {
            return WalletNfcProtocol.UNAVAILABLE.clone();
        }
        if (result.addressShared) AndroidUtilities.runOnUIThread(() -> {
            if (receiver == current && !ApplicationLoader.mainInterfacePaused && !SharedConfig.appLocked && !SharedConfig.isWaitingForPasscodeEnter) current.shared.run();
        });
        return result.response;
    }

    public Availability read(String ownAddress, ReaderCallback callback) {
        stop();
        Availability availability = availability();
        if (availability != Availability.READY) return availability;
        int token = generation.get(); reading.set(true);
        try {
            adapter.enableReaderMode(activity, tag -> discovered(tag, token, ownAddress, callback),
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null);
            readerModeEnabled = true;
        } catch (RuntimeException e) { reading.set(false); FileLog.e(e); return Availability.UNSUPPORTED; }
        // Automatic scanning lasts only for the resumed recipient-selection screen.
        return Availability.READY;
    }

    private void discovered(Tag tag, int token, String ownAddress, ReaderCallback callback) {
        if (generation.get() != token || !reading.get() || !busy.compareAndSet(false, true)) return;
        IsoDep link = IsoDep.get(tag);
        try {
            if (link == null) return;
            connection = link;
            if (generation.get() != token || !reading.get()) return;
            link.connect(); link.setTimeout(2000);
            WalletNfcProtocol.Peer peer = WalletNfcProtocol.decode(link.transceive(WalletNfcProtocol.selectCommand()));
            if (peer == null || !WalletEngine2.isValidRecipientAddress(peer.address)
                    || GramWallet.addressEquals(ownAddress, peer.address)) throw new IOException("Invalid NFC wallet destination");
            if (generation.get() != token || !reading.get()) return;
            if (!WalletNfcProtocol.isOk(link.transceive(peer.acknowledgeCommand()))) throw new IOException("NFC acknowledgement failed");
            if (!reading.compareAndSet(true, false)) return;
            AndroidUtilities.runOnUIThread(() -> {
                if (generation.get() != token || ApplicationLoader.mainInterfacePaused || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) return;
                stop(); callback.onAddress(peer.address);
            });
        } catch (IOException | RuntimeException e) {
            AndroidUtilities.runOnUIThread(() -> {
                if (generation.get() == token && reading.get() && SystemClock.elapsedRealtime() - lastErrorAt > 2000) {
                    lastErrorAt = SystemClock.elapsedRealtime(); callback.onError();
                }
            });
        } finally {
            if (connection == link) connection = null;
            if (link != null) try { link.close(); } catch (IOException ignored) { }
            busy.set(false);
        }
    }

    public void stop() {
        generation.incrementAndGet();
        stopReceiving();
        reading.set(false);
        if (adapter != null && readerModeEnabled) {
            readerModeEnabled = false;
            try { adapter.disableReaderMode(activity); } catch (RuntimeException e) { FileLog.e(e); }
        }
        IsoDep link = connection; connection = null;
        if (link != null) Utilities.globalQueue.postRunnable(() -> { try { link.close(); } catch (IOException ignored) { } });
    }
}
