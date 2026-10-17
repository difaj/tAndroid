package org.telegram.tgnet.tl;

import java.util.ArrayList;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

/** Layer 232 response constructors, verified against ios/232/layer232_enriched.tl. */
public class TL_layer232 {
    /** Includes pinned_msg_id from the current iOS 232 schema; old channels remain readable. */
    public static class Channel extends TLRPC.TL_channel {
        public static final int constructor = 0x7ad15240;
        public int pinned_msg_id;

        @Override protected int getConstructor() { return constructor; }

        @Override public void readParams(InputSerializedData s, boolean e) {
            super.readParams(s, e);
            if (hasFlag(flags2, FLAG_22)) pinned_msg_id = s.readInt32(e);
        }

        @Override public void serializeToStream(OutputSerializedData s) {
            flags2 = setFlag(flags2, FLAG_22, pinned_msg_id != 0);
            super.serializeToStream(s);
            if (hasFlag(flags2, FLAG_22)) s.writeInt32(pinned_msg_id);
        }
    }

    public static class CardSticker extends TLObject {
        public static final int constructor = 0xf3010cba;
        public TL_stories.MediaAreaCoordinates coordinates;
        public TL_wallet.nftItem nft;
        public static CardSticker TLdeserialize(InputSerializedData s, int c, boolean e) {
            return TLdeserialize(CardSticker.class, c == constructor ? new CardSticker() : null, s, c, e);
        }
        public void readParams(InputSerializedData s, boolean e) {
            coordinates = TL_stories.MediaAreaCoordinates.TLdeserialize(s, s.readInt32(e), e);
            nft = TL_wallet.nftItem.TLdeserialize(s, s.readInt32(e), e);
        }
        public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor); coordinates.serializeToStream(s); nft.serializeToStream(s);
        }
    }
    public static class Card extends TLObject {
        public static final int constructor = 0x8ed043b1;
        public int flags;
        public ArrayList<CardSticker> stickers = new ArrayList<>();
        public TL_wallet.nftItem bg_nft_item;
        public String address;
        public long balance;
        public static Card TLdeserialize(InputSerializedData s, int c, boolean e) {
            return TLdeserialize(Card.class, c == constructor ? new Card() : null, s, c, e);
        }
        public void readParams(InputSerializedData s, boolean e) {
            flags = s.readInt32(e);
            if (hasFlag(flags, FLAG_0)) stickers = Vector.deserialize(s, CardSticker::TLdeserialize, e);
            if (hasFlag(flags, FLAG_1)) bg_nft_item = TL_wallet.nftItem.TLdeserialize(s, s.readInt32(e), e);
            address = s.readString(e); balance = s.readInt64(e);
        }
        public void serializeToStream(OutputSerializedData s) {
            s.writeInt32(constructor); flags = setFlag(flags, FLAG_0, !stickers.isEmpty());
            flags = setFlag(flags, FLAG_1, bg_nft_item != null); s.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) Vector.serialize(s, stickers);
            if (bg_nft_item != null) bg_nft_item.serializeToStream(s);
            s.writeString(address); s.writeInt64(balance);
        }
    }
    public static class UserFull extends TLRPC.TL_userFull_layer230 {
        public static final int constructor = 0x5b550ec6;
        public Card wallet_card;
        @Override public void readParams(InputSerializedData s, boolean e) {
            super.readParams(s, e);
            if (hasFlag(flags2, FLAG_19)) wallet_card = Card.TLdeserialize(s, s.readInt32(e), e);
            if (wallet_card != null) gram_address = wallet_card.address;
        }
        @Override public void serializeToStream(OutputSerializedData s) {
            flags2 = setFlag(flags2, FLAG_19, wallet_card != null);
            final SerializedData data = new SerializedData();
            try {
                super.serializeToStream(data);
                final byte[] body = data.toByteArray();
                s.writeInt32(constructor); s.writeBytes(body, 4, body.length - 4);
                if (wallet_card != null) wallet_card.serializeToStream(s);
            } finally { data.cleanup(); }
        }
    }
    public static class NoPaidMessagesTokenUsed extends TLRPC.MessageAction {
        public static final int constructor = 0xc1d2f1c6;
        public void serializeToStream(OutputSerializedData s) { s.writeInt32(constructor); }
    }
}
