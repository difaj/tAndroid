package org.telegram.ui.Wallet;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/** Private ISO-DEP address exchange. No amount, user identity or key material. */
public final class WalletNfcProtocol {
    private WalletNfcProtocol() {}
    public static final String AID = "F04752414D57414C01";
    public static final byte[] OK = {(byte) 0x90, 0x00};
    public static final byte[] UNAVAILABLE = {0x69, (byte) 0x85};
    private static final int NONCE_SIZE = 16;
    public static final long HANDSHAKE_TIMEOUT_MS = 30_000;

    public static byte[] selectCommand() {
        byte[] aid = new byte[AID.length() / 2];
        for (int i = 0; i < aid.length; i++) aid[i] = (byte) Integer.parseInt(AID.substring(i * 2, i * 2 + 2), 16);
        byte[] command = new byte[6 + aid.length];
        command[1] = (byte) 0xA4; command[2] = 4; command[4] = (byte) aid.length;
        System.arraycopy(aid, 0, command, 5, aid.length);
        return command;
    }

    public static boolean isAddressShape(String address) {
        return address != null && (address.matches("[A-Za-z0-9_+/-]{48}")
                || address.matches("(?:0|-1):[A-Fa-f0-9]{64}"));
    }

    public static boolean isOk(byte[] response) { return Arrays.equals(response, OK); }

    public static final class Peer {
        public final String address;
        private final byte[] nonce;
        private Peer(String address, byte[] nonce) { this.address = address; this.nonce = nonce; }
        public byte[] acknowledgeCommand() {
            byte[] command = new byte[5 + NONCE_SIZE];
            command[0] = (byte) 0x80; command[1] = 0x10; command[4] = NONCE_SIZE;
            System.arraycopy(nonce, 0, command, 5, NONCE_SIZE);
            return command;
        }
    }

    public static Peer decode(byte[] response) {
        if (response == null || response.length < 2 + 48 + NONCE_SIZE + 2 || response[0] != 1) return null;
        int length = response[1] & 0xff;
        if (length > 67 || response.length != 2 + length + NONCE_SIZE + 2
                || response[response.length - 2] != (byte) 0x90 || response[response.length - 1] != 0) return null;
        String address = new String(response, 2, length, StandardCharsets.US_ASCII);
        if (!isAddressShape(address)) return null;
        return new Peer(address, Arrays.copyOfRange(response, 2 + length, 2 + length + NONCE_SIZE));
    }

    public static final class Result {
        public final byte[] response;
        public final boolean addressShared;
        private Result(byte[] response, boolean shared) { this.response = response; addressShared = shared; }
    }

    /** One foreground wallet session. Acknowledgement is deduplicated and expires. */
    public static final class Session {
        private final String address;
        private final SecureRandom random = new SecureRandom();
        private byte[] nonce;
        private long selectedAt;
        private boolean closed, delivered;
        public Session(String address) {
            if (!isAddressShape(address)) throw new IllegalArgumentException("Invalid address shape");
            this.address = address;
        }
        public synchronized void close() { closed = true; nonce = null; }
        public synchronized Result process(byte[] command, long now) {
            if (closed || command == null) return new Result(UNAVAILABLE.clone(), false);
            byte[] select = selectCommand();
            if (Arrays.equals(command, select) || (command.length == select.length - 1
                    && Arrays.equals(command, Arrays.copyOf(select, select.length - 1)))) {
                if (delivered) return new Result(UNAVAILABLE.clone(), false);
                if (nonce == null || now - selectedAt > HANDSHAKE_TIMEOUT_MS) {
                    nonce = new byte[NONCE_SIZE]; random.nextBytes(nonce); selectedAt = now;
                }
                byte[] bytes = address.getBytes(StandardCharsets.US_ASCII);
                byte[] response = new byte[2 + bytes.length + NONCE_SIZE + 2];
                response[0] = 1; response[1] = (byte) bytes.length;
                System.arraycopy(bytes, 0, response, 2, bytes.length);
                System.arraycopy(nonce, 0, response, 2 + bytes.length, NONCE_SIZE);
                System.arraycopy(OK, 0, response, response.length - 2, 2);
                return new Result(response, false);
            }
            if (command.length == 5 + NONCE_SIZE && command[0] == (byte) 0x80 && command[1] == 0x10
                    && command[2] == 0 && command[3] == 0 && command[4] == NONCE_SIZE) {
                if (nonce == null || now - selectedAt > HANDSHAKE_TIMEOUT_MS
                        || !Arrays.equals(nonce, Arrays.copyOfRange(command, 5, command.length))) {
                    return new Result(UNAVAILABLE.clone(), false);
                }
                boolean notify = !delivered; delivered = true;
                return new Result(OK.clone(), notify);
            }
            return new Result(new byte[] {0x6D, 0x00}, false);
        }
    }
}
