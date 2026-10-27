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
    /** Compatibility name for the channel constructor now supported by upstream. */
    public static class Channel extends TLRPC.TL_channel { }

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
    /** Compatibility name for the user constructor now supported by upstream. */
    public static class UserFull extends TLRPC.TL_userFull { }

    public static class NoPaidMessagesTokenUsed extends TLRPC.MessageAction {
        public static final int constructor = 0xc1d2f1c6;
        public void serializeToStream(OutputSerializedData s) { s.writeInt32(constructor); }
    }
}
