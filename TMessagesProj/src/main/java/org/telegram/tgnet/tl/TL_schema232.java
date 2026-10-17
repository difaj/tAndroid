package org.telegram.tgnet.tl;

import java.util.ArrayList;
import org.telegram.tgnet.*;

/** Wire additions from ios/232/layer232_enriched.tl (iOS 35530).
 * Unknown flag-only meanings are preserved, not inferred. No Feed API is included.
 */
public final class TL_schema232 {
    public static abstract class ShareTheme extends TLObject {
        public static ShareTheme TLdeserialize(InputSerializedData s, int c, boolean e) {
            ShareTheme value;
            switch (c) {
                case InputShareThemeGift.constructor: value = new InputShareThemeGift(); break;
                case ShareThemeBackdrop.constructor: value = new ShareThemeBackdrop(); break;
                case ShareThemeColor.constructor: value = new ShareThemeColor(); break;
                case ShareThemeDefault.constructor: value = new ShareThemeDefault(); break;
                case ShareThemeEmoji.constructor: value = new ShareThemeEmoji(); break;
                case ShareThemeGift.constructor: value = new ShareThemeGift(); break;
                default: value = null;
            }
            return TLdeserialize(ShareTheme.class, value, s, c, e);
        }
    }
    public static abstract class ShareThemeBackdropsResult extends TLObject {
        public static ShareThemeBackdropsResult TLdeserialize(InputSerializedData s, int c, boolean e) {
            ShareThemeBackdropsResult value;
            switch (c) {
                case ShareThemeBackdrops.constructor: value = new ShareThemeBackdrops(); break;
                case ShareThemeBackdropsNotModified.constructor: value = new ShareThemeBackdropsNotModified(); break;
                default: value = null;
            }
            return TLdeserialize(ShareThemeBackdropsResult.class, value, s, c, e);
        }
    }

    /** auth.firebasePnvIntent#df5ac00c */
    public static class AuthFirebasePnvIntent extends TLObject {
        public static final int constructor = 0xdf5ac00c;
        public String nonce;
        public String digital_credential_payload;
        public static AuthFirebasePnvIntent TLdeserialize(InputSerializedData s, int c, boolean e) {
            return TLdeserialize(AuthFirebasePnvIntent.class, c == constructor ? new AuthFirebasePnvIntent() : null, s, c, e);
        }
        @Override public void readParams(InputSerializedData s, boolean e) {
            nonce = s.readString(e);
            digital_credential_payload = s.readString(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            s.writeString(nonce);
            s.writeString(digital_credential_payload);
        }
    }

    /** exportedNoPaidMessagesToken#695dd042 */
    public static class ExportedNoPaidMessagesToken extends TLObject {
        public static final int constructor = 0x695dd042;
        public int flags;
        public String token;
        public String url;
        public Integer expires;
        public static ExportedNoPaidMessagesToken TLdeserialize(InputSerializedData s, int c, boolean e) {
            return TLdeserialize(ExportedNoPaidMessagesToken.class, c == constructor ? new ExportedNoPaidMessagesToken() : null, s, c, e);
        }
        @Override public void readParams(InputSerializedData s, boolean e) {
            flags = s.readInt32(e);
            token = s.readString(e);
            url = s.readString(e);
            if (hasFlag(flags, 1 << 0)) expires = s.readInt32(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            flags = setFlag(flags, 1 << 0, expires != null);
            s.writeInt32(flags);
            s.writeString(token);
            s.writeString(url);
            if (hasFlag(flags, 1 << 0)) s.writeInt32(expires);
        }
    }

    /** inputShareThemeGift#3fb92b5b */
    public static class InputShareThemeGift extends ShareTheme {
        public static final int constructor = 0x3fb92b5b;
        public TL_stars.InputSavedStarGift stargift;
        @Override public void readParams(InputSerializedData s, boolean e) {
            stargift = TL_stars.InputSavedStarGift.TLdeserialize(s, s.readInt32(e), e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            stargift.serializeToStream(s);
        }
    }

    /** inputWalletCardSticker#57df45a7 */
    public static class InputWalletCardSticker extends TLObject {
        public static final int constructor = 0x57df45a7;
        public TL_stories.MediaAreaCoordinates coordinates;
        public String nft_address;
        public static InputWalletCardSticker TLdeserialize(InputSerializedData s, int c, boolean e) {
            return TLdeserialize(InputWalletCardSticker.class, c == constructor ? new InputWalletCardSticker() : null, s, c, e);
        }
        @Override public void readParams(InputSerializedData s, boolean e) {
            coordinates = TL_stories.MediaAreaCoordinates.TLdeserialize(s, s.readInt32(e), e);
            nft_address = s.readString(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            coordinates.serializeToStream(s);
            s.writeString(nft_address);
        }
    }

    /** payments.starGiftDepositPayload#725bb4eb */
    public static class PaymentsStarGiftDepositPayload extends TLObject {
        public static final int constructor = 0x725bb4eb;
        public String nft_address;
        public byte[] payload;
        public long amount;
        public static PaymentsStarGiftDepositPayload TLdeserialize(InputSerializedData s, int c, boolean e) {
            return TLdeserialize(PaymentsStarGiftDepositPayload.class, c == constructor ? new PaymentsStarGiftDepositPayload() : null, s, c, e);
        }
        @Override public void readParams(InputSerializedData s, boolean e) {
            nft_address = s.readString(e);
            payload = s.readByteArray(e);
            amount = s.readInt64(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            s.writeString(nft_address);
            s.writeByteArray(payload);
            s.writeInt64(amount);
        }
    }

    /** shareThemeBackdrop#fd62fbd1 */
    public static class ShareThemeBackdrop extends ShareTheme {
        public static final int constructor = 0xfd62fbd1;
        public int backdrop_id;
        @Override public void readParams(InputSerializedData s, boolean e) {
            backdrop_id = s.readInt32(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            s.writeInt32(backdrop_id);
        }
    }

    /** shareThemeBackdropOption#45c1d652 */
    public static class ShareThemeBackdropOption extends TLObject {
        public static final int constructor = 0x45c1d652;
        public int backdrop_id;
        public String name;
        public int center_color;
        public int edge_color;
        public int pattern_color;
        public int text_color;
        public static ShareThemeBackdropOption TLdeserialize(InputSerializedData s, int c, boolean e) {
            return TLdeserialize(ShareThemeBackdropOption.class, c == constructor ? new ShareThemeBackdropOption() : null, s, c, e);
        }
        @Override public void readParams(InputSerializedData s, boolean e) {
            backdrop_id = s.readInt32(e);
            name = s.readString(e);
            center_color = s.readInt32(e);
            edge_color = s.readInt32(e);
            pattern_color = s.readInt32(e);
            text_color = s.readInt32(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            s.writeInt32(backdrop_id);
            s.writeString(name);
            s.writeInt32(center_color);
            s.writeInt32(edge_color);
            s.writeInt32(pattern_color);
            s.writeInt32(text_color);
        }
    }

    /** shareThemeBackdrops#aaeadce0 */
    public static class ShareThemeBackdrops extends ShareThemeBackdropsResult {
        public static final int constructor = 0xaaeadce0;
        public int hash;
        public ArrayList<ShareThemeBackdropOption> backdrops = new ArrayList<>();
        @Override public void readParams(InputSerializedData s, boolean e) {
            hash = s.readInt32(e);
            backdrops = Vector.deserialize(s, ShareThemeBackdropOption::TLdeserialize, e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            s.writeInt32(hash);
            Vector.serialize(s, backdrops);
        }
    }

    /** shareThemeBackdropsNotModified#7a3f6dff */
    public static class ShareThemeBackdropsNotModified extends ShareThemeBackdropsResult {
        public static final int constructor = 0x7a3f6dff;
        @Override public void readParams(InputSerializedData s, boolean e) {
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
        }
    }

    /** shareThemeColor#1f48bc20 */
    public static class ShareThemeColor extends ShareTheme {
        public static final int constructor = 0x1f48bc20;
        public int color_id;
        @Override public void readParams(InputSerializedData s, boolean e) {
            color_id = s.readInt32(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            s.writeInt32(color_id);
        }
    }

    /** shareThemeDefault#95ace84f */
    public static class ShareThemeDefault extends ShareTheme {
        public static final int constructor = 0x95ace84f;
        @Override public void readParams(InputSerializedData s, boolean e) {
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
        }
    }

    /** shareThemeEmoji#3ab4430a */
    public static class ShareThemeEmoji extends ShareTheme {
        public static final int constructor = 0x3ab4430a;
        public ArrayList<TLRPC.Reaction> emojis = new ArrayList<>();
        @Override public void readParams(InputSerializedData s, boolean e) {
            emojis = Vector.deserialize(s, TLRPC.Reaction::TLdeserialize, e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            Vector.serialize(s, emojis);
        }
    }

    /** shareThemeGift#ebece66d */
    public static class ShareThemeGift extends ShareTheme {
        public static final int constructor = 0xebece66d;
        public TL_stars.StarGift stargift;
        @Override public void readParams(InputSerializedData s, boolean e) {
            stargift = TL_stars.StarGift.TLdeserialize(s, s.readInt32(e), e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            stargift.serializeToStream(s);
        }
    }

    /** auth.initFirebasePnvLogin#644f15c0 */
    public static class AuthInitFirebasePnvLogin extends TLMethod<AuthFirebasePnvIntent> {
        public static final int constructor = 0x644f15c0;
        public int flags;
        public ArrayList<Long> except_ids;
        public int api_id;
        public String api_hash;
        @Override public void readParams(InputSerializedData s, boolean e) {
            flags = s.readInt32(e);
            if (hasFlag(flags, 1 << 0)) except_ids = Vector.deserializeLong(s, e);
            api_id = s.readInt32(e);
            api_hash = s.readString(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            flags = setFlag(flags, 1 << 0, except_ids != null);
            s.writeInt32(flags);
            if (hasFlag(flags, 1 << 0)) Vector.serializeLong(s, except_ids);
            s.writeInt32(api_id);
            s.writeString(api_hash);
        }
        @Override public AuthFirebasePnvIntent deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return AuthFirebasePnvIntent.TLdeserialize(s, c, e);
        }
    }

    /** auth.cancelWebTokenAuthorization#490dada1 */
    public static class AuthCancelWebTokenAuthorization extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x490dada1;
        public String web_auth_token;
        @Override public void readParams(InputSerializedData s, boolean e) {
            web_auth_token = s.readString(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            s.writeString(web_auth_token);
        }
        @Override public TLRPC.Bool deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return TLRPC.Bool.TLdeserialize(s, c, e);
        }
    }

    /** contacts.exportNoPaidMessagesToken#83c586ae */
    public static class ContactsExportNoPaidMessagesToken extends TLMethod<ExportedNoPaidMessagesToken> {
        public static final int constructor = 0x83c586ae;
        public int flags;
        @Override public void readParams(InputSerializedData s, boolean e) {
            flags = s.readInt32(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            s.writeInt32(flags);
        }
        @Override public ExportedNoPaidMessagesToken deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return ExportedNoPaidMessagesToken.TLdeserialize(s, c, e);
        }
    }

    /** contacts.importNoPaidMessagesToken#35a7c6ca */
    public static class ContactsImportNoPaidMessagesToken extends TLMethod<TLRPC.User> {
        public static final int constructor = 0x35a7c6ca;
        public TLRPC.InputUser user_id;
        public String token;
        @Override public void readParams(InputSerializedData s, boolean e) {
            user_id = TLRPC.InputUser.TLdeserialize(s, s.readInt32(e), e);
            token = s.readString(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            user_id.serializeToStream(s);
            s.writeString(token);
        }
        @Override public TLRPC.User deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return TLRPC.User.TLdeserialize(s, c, e);
        }
    }

    /** help.test#c0e202f7 */
    public static class HelpTest extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0xc0e202f7;
        @Override public void readParams(InputSerializedData s, boolean e) {
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
        }
        @Override public TLRPC.Bool deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return TLRPC.Bool.TLdeserialize(s, c, e);
        }
    }

    /** messages.getShareTheme#761b1c73 */
    public static class MessagesGetShareTheme extends TLMethod<ShareTheme> {
        public static final int constructor = 0x761b1c73;
        public TLRPC.InputPeer peer;
        @Override public void readParams(InputSerializedData s, boolean e) {
            peer = TLRPC.InputPeer.TLdeserialize(s, s.readInt32(e), e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            peer.serializeToStream(s);
        }
        @Override public ShareTheme deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return ShareTheme.TLdeserialize(s, c, e);
        }
    }

    /** messages.getShareThemeBackdrops#f4599ef2 */
    public static class MessagesGetShareThemeBackdrops extends TLMethod<ShareThemeBackdropsResult> {
        public static final int constructor = 0xf4599ef2;
        public int hash;
        @Override public void readParams(InputSerializedData s, boolean e) {
            hash = s.readInt32(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            s.writeInt32(hash);
        }
        @Override public ShareThemeBackdropsResult deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return ShareThemeBackdropsResult.TLdeserialize(s, c, e);
        }
    }

    /** messages.setShareTheme#698a9b1a */
    public static class MessagesSetShareTheme extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x698a9b1a;
        public TLRPC.InputPeer peer;
        public ShareTheme theme;
        @Override public void readParams(InputSerializedData s, boolean e) {
            peer = TLRPC.InputPeer.TLdeserialize(s, s.readInt32(e), e);
            theme = ShareTheme.TLdeserialize(s, s.readInt32(e), e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            peer.serializeToStream(s);
            theme.serializeToStream(s);
        }
        @Override public TLRPC.Bool deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return TLRPC.Bool.TLdeserialize(s, c, e);
        }
    }

    /** payments.depositStarGift#c0a2ea9f */
    public static class PaymentsDepositStarGift extends TLMethod<PaymentsStarGiftDepositPayload> {
        public static final int constructor = 0xc0a2ea9f;
        public int flags;
        public TLRPC.InputPeer peer;
        public String slug;
        @Override public void readParams(InputSerializedData s, boolean e) {
            flags = s.readInt32(e);
            peer = TLRPC.InputPeer.TLdeserialize(s, s.readInt32(e), e);
            slug = s.readString(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            s.writeInt32(flags);
            peer.serializeToStream(s);
            s.writeString(slug);
        }
        @Override public PaymentsStarGiftDepositPayload deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return PaymentsStarGiftDepositPayload.TLdeserialize(s, c, e);
        }
    }

    /** payments.displayStarGift#4aa5f0fe */
    public static class PaymentsDisplayStarGift extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x4aa5f0fe;
        public int flags;
        public TLRPC.InputPeer peer;
        public String slug;
        @Override public void readParams(InputSerializedData s, boolean e) {
            flags = s.readInt32(e);
            if (hasFlag(flags, 1 << 0)) peer = TLRPC.InputPeer.TLdeserialize(s, s.readInt32(e), e);
            slug = s.readString(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            flags = setFlag(flags, 1 << 0, peer != null);
            s.writeInt32(flags);
            if (hasFlag(flags, 1 << 0)) peer.serializeToStream(s);
            s.writeString(slug);
        }
        @Override public TLRPC.Bool deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return TLRPC.Bool.TLdeserialize(s, c, e);
        }
    }

    /** payments.withdrawStarGift#20109cb3 */
    public static class PaymentsWithdrawStarGift extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x20109cb3;
        public int flags;
        public TL_stars.InputSavedStarGift stargift;
        public TLRPC.InputCheckPasswordSRP password;
        public byte[] tmp_password;
        @Override public void readParams(InputSerializedData s, boolean e) {
            flags = s.readInt32(e);
            stargift = TL_stars.InputSavedStarGift.TLdeserialize(s, s.readInt32(e), e);
            password = TLRPC.InputCheckPasswordSRP.TLdeserialize(s, s.readInt32(e), e);
            if (hasFlag(flags, 1 << 1)) tmp_password = s.readByteArray(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            flags = setFlag(flags, 1 << 1, tmp_password != null);
            s.writeInt32(flags);
            stargift.serializeToStream(s);
            password.serializeToStream(s);
            if (hasFlag(flags, 1 << 1)) s.writeByteArray(tmp_password);
        }
        @Override public TLRPC.Bool deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return TLRPC.Bool.TLdeserialize(s, c, e);
        }
    }

    /** wallet.setCardStickers#068509ac */
    public static class WalletSetCardStickers extends TLMethod<TL_layer232.Card> {
        public static final int constructor = 0x068509ac;
        public int flags;
        public ArrayList<InputWalletCardSticker> stickers = new ArrayList<>();
        public String bg_nft_address;
        @Override public void readParams(InputSerializedData s, boolean e) {
            flags = s.readInt32(e);
            stickers = Vector.deserialize(s, InputWalletCardSticker::TLdeserialize, e);
            if (hasFlag(flags, 1 << 0)) bg_nft_address = s.readString(e);
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor);
            flags = setFlag(flags, 1 << 0, bg_nft_address != null);
            s.writeInt32(flags);
            Vector.serialize(s, stickers);
            if (hasFlag(flags, 1 << 0)) s.writeString(bg_nft_address);
        }
        @Override public TL_layer232.Card deserializeResponseT(InputSerializedData s, int c, boolean e) {
            return TL_layer232.Card.TLdeserialize(s, c, e);
        }
    }
}

