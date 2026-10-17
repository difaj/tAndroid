package org.telegram.ui.Wallet;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

/** NFC system binding only; a background service never opens or sends a transfer. */
public final class WalletNfcService extends HostApduService {
    @Override public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        return WalletNfcController.processCommand(commandApdu);
    }
    @Override public void onDeactivated(int reason) {
        // Keep the short-lived nonce to allow ACK retransmission after a dropped link.
    }
}
